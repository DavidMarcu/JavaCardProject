import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Locale;
import java.util.stream.Stream;

import com.sun.javacard.apduio.Apdu;
import com.sun.javacard.apduio.CadClientInterface;
import com.sun.javacard.apduio.CadDevice;
import com.sun.javacard.apduio.CadTransportException;

public class Application {

    private CadClientInterface cadClientInterface;
    private static final byte VERIFY = (byte) 0x20;
    private static final byte CREDIT = (byte) 0x30;
    private static final byte DEBIT = (byte) 0x40;
    private static final byte GET_BALANCE = (byte) 0x50;
    private static final byte GET_CARDHOLDER_METHODS = (byte) 0x70;
    private static final byte ENCRYPT_PIN = (byte) 0x90;
    private static final byte CHECK_ENCRYPTED_PIN = (byte) 0x00;

    private void byteArrayHexPrint(String firstLine, byte[] byteArray) {
        System.out.println(firstLine);
        for (byte element : byteArray) {
            System.out.format(Locale.getDefault(), "0x%02x ", element);
        }
        System.out.println();
    }

    private Apdu stringCommandToApduCommand(String command) {
        Apdu apdu = new Apdu();
        String[] splittedCommand = command.split("\\s?0x");
        //Formed header of apdu command
        byte[] byteCommand = new byte[4];
        for (int i = 1; i < 5; i++) {
            int commandPart = Integer.parseUnsignedInt(splittedCommand[i], 16);
            byteCommand[i - 1] = (byte) commandPart;
        }
        apdu.command = byteCommand;

        //Formed body of apdu command
        int dataInSize = Integer.parseUnsignedInt(splittedCommand[5], 16);
        apdu.setLc(dataInSize);
        if (dataInSize > 0) {
            byte[] dataIn = new byte[dataInSize];
            for (int i = 6; i < splittedCommand.length - 1; i++) {
                int commandPart = Integer.parseUnsignedInt(splittedCommand[i], 16);
                dataIn[i - 6] = (byte) commandPart;
            }
            apdu.setDataIn(dataIn);
        }

        String lastCommandUnit = splittedCommand[splittedCommand.length - 1]
                .substring(0, splittedCommand[splittedCommand.length - 1].length() - 1);
        int expectedResponseLength = Integer.parseUnsignedInt(lastCommandUnit, 16);
        apdu.setLe(expectedResponseLength);

        return apdu;
    }

    private void cardConnection() throws IOException, CadTransportException {
        Socket socket = new Socket("127.0.0.1", 9025);
        InputStream readStream = socket.getInputStream();
        OutputStream writeStream = socket.getOutputStream();
        cadClientInterface = CadDevice.getCadClientInstance(CadDevice.PROTOCOL_T1, readStream, writeStream);
        byte[] answerToReset = cadClientInterface.powerUp();
        byteArrayHexPrint("Answer to reset bytes: ", answerToReset);
    }

    private void runCommand(String command, boolean output) {
        Apdu apduCommand = stringCommandToApduCommand(command);
        try {
            cadClientInterface.exchangeApdu(apduCommand);
        } catch (IOException | CadTransportException e) {
            e.printStackTrace();
        }

        if (output) {
            String message;
            switch (apduCommand.command[1]){
                case GET_CARDHOLDER_METHODS:
                    message = "Cardholder methods response: ";
                    break;
                case ENCRYPT_PIN:
                    message = "Encrypt pin response: ";
                    break;
                case CHECK_ENCRYPTED_PIN:
                    message = "Check encrypted pin response: ";
                    break;
                default:
                    message = "Apdu response: ";
            }
            byte[] apduOutput = apduCommand.getResponseApduBytes();
            byteArrayHexPrint(message, apduOutput);
        }
    }

    private void runScripts(File scriptFile) throws IOException {
        runScripts(scriptFile, false);
    }

    private void runScripts(File scriptFile, boolean output) throws IOException {
        BufferedReader scriptReader = new BufferedReader(new InputStreamReader(new FileInputStream(scriptFile)));
        Stream<String> scriptStream = scriptReader.lines();
        scriptStream.filter(line -> !line.startsWith("//") && !line.isEmpty()).forEach(line -> runCommand(line, output));
        scriptReader.close();
    }

    public static void main(String[] args) {
        Application terminal = new Application();
        try {
            terminal.cardConnection();
            terminal.runScripts(new File("C:\\Practice\\Wallet\\applet\\apdu_scripts\\cap-com.sun.jcclassic.samples.wallet.script"));
            terminal.runScripts(new File("C:\\Practice\\Wallet\\applet\\apdu_scripts\\wallet.scr"), true);
            terminal.cadClientInterface.powerDown();
        } catch (IOException | CadTransportException e) {
            e.printStackTrace();
        }
    }
}

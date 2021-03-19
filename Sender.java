import java.io.File;
import java.io.FileInputStream;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
public class Sender extends Application {
    //global sockets to check if they are open in gui before initiating protocol
    DatagramSocket rcvrSocket;
    DatagramSocket sndrSocket; 
    //Label global so we
    Label totalTimeLabel; 
    private  class Connector extends Task<Long> {

        private String hostIp; 
        private int rcvrPort; 
        private int sndrPort; 
        private File file; 
        private int dtgmSize; 
        private int timeout; 

        public  Connector ( String hostIp, int rcvrPort,  int sndrPort, File file,int dtgmSize, int timeout){
            this.hostIp = hostIp; 
            this.rcvrPort = rcvrPort; 
            this.sndrPort = sndrPort; 
            this.file = file; 
            this.dtgmSize = dtgmSize; 
            this.timeout = timeout; 


        }
        @Override
        public Long call() throws Exception{
            long startTime = System.currentTimeMillis();
            try {
                InetAddress ip = InetAddress.getByName(hostIp);
                // CREATE SOCKETS
                rcvrSocket = new DatagramSocket();
                sndrSocket = new DatagramSocket(sndrPort);

                sndrSocket.setSoTimeout(timeout);
                // initiate handshaking using a modified TCP handshake
                byte synByte = 1;
                byte xByte = (byte) ((Math.random() * 50) + 1);
                byte trans1[] = { synByte, xByte };
                // send request for connection
                DatagramPacket sentPckt = new DatagramPacket(trans1, trans1.length, ip, rcvrPort);
                rcvrSocket.send(sentPckt);

                DatagramPacket rcvdPckt = new DatagramPacket(new byte[4], 4);
                int i = 0 ; 
                boolean ackReceived = false; 
                while (!ackReceived && i < 3 ){
                    try{
                        sndrSocket.receive(rcvdPckt);
                        ackReceived = true; 
                    }catch(Exception error) {

                    }
                    i++;
                }
                
                byte ack[] = rcvdPckt.getData();
                // server verified awake
                // loop until correct acknowledgement is sent.
                while (ack[0] != 1 || ack[2] != 1 || ack[3] != (byte) (xByte + 1)) {// check to make sure bytes were
                                                                                    // acknowledged
                    rcvrSocket.send(sentPckt);
                    sndrSocket.receive(rcvdPckt);
                    ack = rcvdPckt.getData();
                }
                // clarify to server this is a valid sender
                byte size[] = ByteBuffer.allocate(4).putInt(dtgmSize).array();
                byte trans2[] = { synByte, (byte) (ack[1] + 1), 0, 0 , 0 ,0};
                System.arraycopy(size, 0 , trans2, 2, size.length);
                sentPckt = new DatagramPacket(trans2, trans2.length, ip, rcvrPort);
                rcvrSocket.send(sentPckt);

                // handshaking complete , begin data transmission
                
                int fileSize = (int) file.length();
                if (fileSize <=0 ){
                    sndrSocket.close(); 
                    rcvrSocket.close();
                    throw new NullPointerException("File Doesn't Exist");
                }
                //get the file as bytes
                byte[] data = new byte[fileSize];
                FileInputStream fis = new FileInputStream(file);
                fis.read(data); 
                fis.close();
                /*for testing 
                byte data[] = "THIS IS TEN ALIVE STAR WHILE\n\nSTART HUBSABND POSITION\n  LET 1234565412341".getBytes();
                 */
                // find file and stuff for now use fake string
                int start = 0;
                int end = dtgmSize;
                boolean eotSent = false;
                byte lastAck = -1; 
                byte currentAck = 1;
                byte temp[] = new byte[dtgmSize];
                byte sentInfo[] = new byte[dtgmSize];
                //creating both packets
                DatagramPacket sentPckt0 = new DatagramPacket(sentInfo, sentInfo.length, ip, rcvrPort);               
                DatagramPacket sentPckt1 = new DatagramPacket(sentInfo, sentInfo.length, ip, rcvrPort);
                while (!eotSent  ) {
                    if (end > data.length) { // finding the last packet.
                        end = data.length+1 ;
                        eotSent = true; 
                        sentInfo = new byte[end - start]; //make the last packet the right size, so it doesnt send padded zeros to last message
                    }else {
                        sentInfo = new byte[dtgmSize];
                    }
                    // if current ack is the same as lastack, resend packet
                    if (currentAck == 1) {
                        // send packet1 if ack1 was last ack
                        temp = Arrays.copyOfRange(data, start, end - 1); // copy data from file to temp 
                        sentInfo[0] = (byte) 0; //make first byte of the sentInfo the seq number
                        System.arraycopy(temp, 0 , sentInfo, 1, temp.length); 
                        sentPckt0 = new DatagramPacket(sentInfo, sentInfo.length, ip, rcvrPort);
                        rcvrSocket.send(sentPckt0);
                        //increase end and start to grab next chunk 
                        start = start + dtgmSize - 1;
                        end = end + dtgmSize - 1;
                    } else{
                        // send packet one if ack 0
                        temp = Arrays.copyOfRange(data, start, end - 1);// copy data from file to temp 
                        sentInfo[0] = (byte) 1;//make first byte of the sentInfo the seq number
                        System.arraycopy(temp, 0 , sentInfo, 1, temp.length);
                        sentPckt1 = new DatagramPacket(sentInfo, sentInfo.length, ip, rcvrPort);
                        rcvrSocket.send(sentPckt1);
                        //increase end and start to grab next chunk 
                        start = start + dtgmSize - 1;
                        end = end + dtgmSize - 1;
                    }

                    // to make sure Arrays.copyOfRange(...) does cause array index out of bounds

                    // loop till it receives acknowledgement
                    boolean acked = false; 
                    while (!acked){
                        try {
                            sndrSocket.receive(rcvdPckt);
                            ack = rcvdPckt.getData();
                            if (ack[0] != currentAck){//filters out previous ack messages. 
                                currentAck = ack[0];                        
                                acked = true; 
                            }
                        } catch (Exception ex) {// socket timed out. must resend packet
                            if (currentAck == 1) { //resend packed based on what seq number we are on. 
                                rcvrSocket.send(sentPckt0);  
                            } else {
                                rcvrSocket.send(sentPckt1);
                            }
                        }                        
                    }
                    //System.out.printf("currentAck= %d \nLastAck= %d \n ", currentAck,lastAck);
                }
                //loop has ended send EOT message. 
                byte eot[]  ={-99}; 
                sentPckt1 = new DatagramPacket(eot, eot.length, ip, rcvrPort);
                rcvrSocket.send(sentPckt1);

                sndrSocket.close();
                rcvrSocket.close();
            }catch (SocketTimeoutException error){
                throw new Exception("Handshaking failed. No receiver found.");
            }catch(BindException error){
                throw new Exception("Socket you are trying to give to sender port is already in use. "); 
            }catch (SocketException error) {
                throw new Exception("Error with socket"); 
            }catch (UnknownHostException error){
                throw new Exception("Invalid Ip address.");
            } finally {
                sndrSocket.close();
                rcvrSocket.close();
            }
            long endTime = System.currentTimeMillis();
            long totalTime = (endTime - startTime);
            return totalTime;
        }
    }
    public static void main(String args[]) throws Exception {
        launch(args);
    }
    public void popUp(String errorMessage){
        Alert popUp = new Alert(AlertType.ERROR); 
        popUp.setHeaderText(errorMessage);
        popUp.setTitle("Error Message");
        popUp.showAndWait(); 
    }
    @Override
    public void start(Stage primaryStage) throws Exception {
        Stage window = primaryStage;
        window.setTitle("Sender Application");
        BorderPane layout = new BorderPane();
        GridPane form = new GridPane();
        form.setPadding(new Insets(10, 10, 10, 10));
        form.setVgap(8);
        form.setHgap(10);

        // creating Ip row
        Label ipLabel = new Label("IP address:");
        GridPane.setConstraints(ipLabel, 0, 0);
        TextField ipInput = new TextField();
        GridPane.setConstraints(ipInput, 1, 0);

        // creating ReceiverPort row
        Label rcvrLabel = new Label("UDP Receiver Port Number:");
        GridPane.setConstraints(rcvrLabel, 0, 1);
        TextField rcvrInput = new TextField();
        rcvrInput.setPromptText("####");
        GridPane.setConstraints(rcvrInput, 1, 1);

        // creating Sender Port Row
        Label sndrLabel = new Label("UDP Sender Port Number:");
        GridPane.setConstraints(sndrLabel, 0, 2);
        TextField sndrInput = new TextField();
        sndrInput.setPromptText("####");
        GridPane.setConstraints(sndrInput, 1, 2);

        // creating filenaming row
        Label fileLabel = new Label("File Name:");
        GridPane.setConstraints(fileLabel, 0, 3);
        TextField fileInput = new TextField();
        GridPane.setConstraints(fileInput, 1, 3);

        // creating Size of datagram row
        Label sizeLabel = new Label("Size of Datagram:");
        GridPane.setConstraints(sizeLabel, 0, 4);
        TextField sizeInput = new TextField();
        GridPane.setConstraints(sizeInput, 1, 4);

        // creating timeout row
        Label timeoutLabel = new Label("Timeout (milliseconds):");
        GridPane.setConstraints(timeoutLabel, 0, 5);
        TextField timeoutInput = new TextField();
        GridPane.setConstraints(timeoutInput, 1, 5);
        
        //Run time label 
        Label timeTitleLabel =  new Label("Total Run-Time (milliseconds): ");
        GridPane.setConstraints(timeTitleLabel, 0, 7);
        totalTimeLabel = new Label("");
        GridPane.setConstraints(totalTimeLabel, 1, 7);

        // creating transfer buttom row
        Button transButton = new Button("Transfer");
        GridPane.setConstraints(transButton, 2, 6);
        transButton.setOnAction(e -> { // initiate transfer protocol
            String hostIp = "";
            int rcvrPort = -9999;
            int sndrPort = -9999;
            String fileName = "";
            int dtgmSize = -9999;
            int timeout = -9999;
            File file= null;
            try {// incase the values dont parse properly.
                //get all the data fromn the GUI
                hostIp = ipInput.getText();
                rcvrPort = Integer.parseInt(rcvrInput.getText());
                sndrPort = Integer.parseInt(sndrInput.getText());
                fileName = fileInput.getText();
                dtgmSize = Integer.parseInt(sizeInput.getText());
                timeout = Integer.parseInt(timeoutInput.getText());

                //*IMPORTANT* throw errors instead of calling popUp() because we dont want the connector thread to run anyways.

                //check if the File exists. 
                file = new File(fileName); 
                int fileSize = (int) file.length();
                if (fileSize <=0 ){
                    throw new NullPointerException("File Doesn't Exist");
                }
                if(dtgmSize <=1 ){//make sure we can have at least one byte and the seq number
                    throw new ArithmeticException("Place holder Exception.");
                }

                if (sndrSocket == null || sndrSocket.isClosed()){

                    Connector conn = new Connector(hostIp, rcvrPort, sndrPort, file, dtgmSize, timeout);
                    new Thread(conn).start();
                    conn.setOnSucceeded(e2->{
                        totalTimeLabel.setText(String.valueOf(conn.getValue()));
                    });
                    conn.setOnFailed(e2 -> {
                        popUp(conn.getException().getMessage());
                    });
                           
                }else {
                    popUp("Sorry, Can only send one file at a time. ");   
                }
            
            }catch(ArithmeticException error){
                popUp("Datagram Size must be >= 2");
            }catch(NumberFormatException error){
                popUp("Invalid Integer values.");
            }catch(NullPointerException error){
                popUp("Invalid File.");
            }catch (Exception error) {
                popUp("Invalid Input");
            }
        });


        form.getChildren().addAll(ipLabel, ipInput, rcvrLabel, rcvrInput, sndrLabel, sndrInput, fileLabel, fileInput,
                sizeLabel, sizeInput, timeoutLabel, timeoutInput, transButton, totalTimeLabel, timeTitleLabel);
        layout.setTop(form);
        Scene mainScene = new Scene(layout, 500, 300);
        window.setScene(mainScene);
        window.show();
    }

}
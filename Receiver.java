import javafx.application.Application;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.control.TextArea;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

import java.net.BindException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Scanner;
import javafx.scene.control.ChoiceBox;
import java.net.InetAddress;
import java.io.FileOutputStream;

public class Receiver extends Application{

    DatagramSocket getSocket;
    DatagramSocket sendSocket;

    private class Connector extends Task<Void> {
        //public  IntegerProperty inOrder = new SimpleIntegerProperty(0);
        int inOrder = 0;
        private InetAddress sendIP;
        private int sendPort;
        private int getPort;
        private String getFileName;
        private Boolean unstableCon;
            

            public  Connector (InetAddress sendIP, int sendPort, int getPort, String getFileName, Boolean unstableCon){
                this.sendIP = sendIP; 
                this.sendPort = sendPort; 
                this.getPort = getPort; 
                this.getFileName = getFileName; 
                this.unstableCon = unstableCon; 
            }

            public Void call() throws Exception{
                
                try {
                    //Update GUI
                    updateMessage(String.valueOf(0));                

                    //Sockets for sending and receiving
                    getSocket = new DatagramSocket(getPort); 
                    sendSocket = new DatagramSocket(); 
                    
                    //Packet for handshaking
                    //Data is stored in order: 
                    DatagramPacket getPacketShake = new DatagramPacket(new byte[1024], 1024);
                    //Packet sync number tracker
                    byte lastAck = -1; 
                    //Sequence number tracker
                    int seqNum = 0;
                    //Value to validate user
                    byte yByte = (byte) ((Math.random() * 50) + 1); 
                    
                    //Get initial packet
                    getSocket.receive(getPacketShake);

                    //Create object to hold the received data during handshaking
                    //Data is stored in order: (syncByte, PacketNum, packetDataSize (on second packet))
                    byte getData[] = getPacketShake.getData(); 
                    //Create acknowledgement packet
                    byte ack[] = {1, yByte, getData[0], (byte)(getData[1] + 1)}; 
                    //Talk back 
                    //InetAddress sndrIp = InetAddress.getByName( "localhost" );
                    DatagramPacket sendPacket = new DatagramPacket(ack, ack.length, sendIP, sendPort); 
                    sendSocket.send(sendPacket);
                    //Get second received packet
                    getSocket.receive(getPacketShake);
                    getData = getPacketShake.getData();
                    //Wait for a valid response if a duplicate is received
                    while (getData[0] != 1 || getData[1] != (byte)(yByte + 1 )){
                        sendSocket.send(sendPacket);
                        getSocket.receive(getPacketShake);
                        getData = getPacketShake.getData();
                    }
                    //System.out.println("HandShake complete"); 

                    //Get data size from second packet and make new packet for data
                    byte dataSize[] = Arrays.copyOfRange(getData, 2, 6);
                    int dataSize2 = ByteBuffer.wrap(dataSize).getInt();
                    DatagramPacket getPacket = new DatagramPacket(new byte[dataSize2], dataSize2);
                    byte dataArray[];
                    FileOutputStream fos = new FileOutputStream(this.getFileName);

                    //byte stream[] = new byte[(int)(getData[2])]; 

                    
                    while( getData[0] != -99 || getData.length != 1 ){
                        getSocket.receive(getPacket);
                        getData = Arrays.copyOfRange(getPacket.getData(), 0, getPacket.getLength());

                        //Drop every 10th packet
                        if (seqNum == 9 && this.unstableCon == true){
                            seqNum = 0;

                        } else {
                            //Add new data to the new file
                            if (getData[0] != lastAck){
                                dataArray = Arrays.copyOfRange(getData, 1, getPacket.getLength());                    
                                fos.write(dataArray);
                                //System.out.println(String.format("|%s|", new String(dataArray))); 
                                //inOrder.add(1); //Increment packet counter
                                updateMessage(String.valueOf(++inOrder));
                            }          

                            //Send acknowledgement until a new packet is received.
                            if(getData[0]== lastAck){
                                sendSocket.send(sendPacket);
                                
                            //Sync byte is 0, send acknowledgement
                            }else if (getData[0]==(byte) 0){
                                byte ack0[] = {0}; 
                                sendPacket = new DatagramPacket(ack0, ack0.length, this.sendIP, this.sendPort);
                                sendSocket.send(sendPacket); 
                                lastAck = 0; 
                                if (this.unstableCon == true){
                                    seqNum += 1;
                                }
                                
                            
                            //Sync byte is 1, send acknowledgement
                            }else if (getData[0] == (byte)1){
                                byte ack1[] = {1}; 
                                sendPacket = new DatagramPacket(ack1, ack1.length, this.sendIP, this.sendPort);
                                sendSocket.send(sendPacket);
                                lastAck = 1; 
                                if (this.unstableCon == true){
                                    seqNum += 1;
                                }
                            }

                        }

                        //Drop every 10th packet
                        // if (seqNum == 9){
                        //     seqNum = 0;
                        // } 

                    }

                    //System.out.println("FINISHED");

                    fos.close();
                    getSocket.close();
                    sendSocket.close();

                }catch(BindException error){
                    throw new Exception("Socket you are trying to give to receiver port is already in use. "); 
                }catch (SocketException error) {
                    throw new Exception("Error with socket"); 
                } finally {
                    if (getSocket != null){
                        getSocket.close();
                    }
                    if (sendSocket != null){
                        sendSocket.close();
                    }
                }

                return null;
            }
        }
    public static void main(String args[])throws Exception {
        launch(args);
    }

    //Error message
    public void popUp(String errorMessage){
        Alert popUp = new Alert(AlertType.ERROR); 
        popUp.setHeaderText(errorMessage);
        popUp.setTitle("Error Message");
        popUp.showAndWait(); 
    }


    @Override
    public void start(Stage primaryStage)throws Exception{
        
        //Main window
        Stage window = primaryStage; 
        window.setTitle("Receiver Application");
        BorderPane layout  = new BorderPane(); 
        GridPane form = new GridPane();
        form.setPadding(new Insets(10,10,10,10)); 
        form.setVgap(8); 
        form.setHgap(10);

        
        //Sender IP Fields
        Label sendIPLabel = new Label("IP of the sender:");
        GridPane.setConstraints(sendIPLabel, 0, 0);

        TextField sendIPInput = new TextField();
        GridPane.setConstraints(sendIPInput, 1, 0);


        //Sender Port Number
        Label sendPortLabel = new Label("UDP Sender Port Number:");
        GridPane.setConstraints(sendPortLabel, 0, 1);

        TextField sendPortInput = new TextField();
        sendPortInput.setPromptText("####");
        GridPane.setConstraints(sendPortInput, 1, 1);


        //Receiver Port Number
        Label getPortLabel = new Label("UDP Receiver Port Number:");
        GridPane.setConstraints(getPortLabel, 0, 2);

        TextField getPortInput = new TextField();
        getPortInput.setPromptText("####");
        GridPane.setConstraints(getPortInput, 1, 2);


        //File Name
        Label getFNameLabel = new Label("Received File Name:");
        GridPane.setConstraints(getFNameLabel, 0, 3);

        TextField getFNameInput = new TextField();
        getFNameInput.setPromptText("filename.filetype");
        GridPane.setConstraints(getFNameInput, 1, 3);


        //Unreliable checkbox
        Label conLabel = new Label("Unreliable Connection:");
        GridPane.setConstraints(conLabel, 0, 4);

        CheckBox conBox = new CheckBox();
        GridPane.setConstraints(conBox, 1, 4);


        //Current number of received in-order packets
        Label getNumLabel = new Label("Current number of received in-order packets:");
        GridPane.setConstraints(getNumLabel, 0, 5);

        Label getNumLabel2 = new Label();
        GridPane.setConstraints(getNumLabel2, 1, 5);
        


        //Confirm Settings Button
        Button confirmButton = new Button("Confirm Settings"); 
        GridPane.setConstraints(confirmButton, 1, 6);
        

        form.getChildren().addAll(sendIPLabel, sendIPInput, sendPortLabel, sendPortInput, getPortLabel, getPortInput, getFNameLabel, getFNameInput, getNumLabel, getNumLabel2, confirmButton, conBox, conLabel);
        layout.setTop(form);
        
        Scene mainScene = new Scene(layout,500,300);
        window.setScene(mainScene);
        window.show();
        
        
        confirmButton.setOnAction(e->{ //initiate transfer protocol
            
            //Disable text fields and confirm button.
            // sendIPInput.setDisable(true);
            // sendPortInput.setDisable(true);
            // getPortInput.setDisable(true);
            // getFNameInput.setDisable(true);
            // confirmButton.setDisable(true);
            // conBox.setDisable(true);
            
            try {

                //Initialize values
                InetAddress sendIP = InetAddress.getByName( "localhost" );
                int sendPort = -9999;
                int getPort = -9999;
                String getFileName = "";
                Boolean unstableCon = false;
                
                //Get entered fields
                sendIP = InetAddress.getByName(sendIPInput.getText());
                sendPort = Integer.valueOf(sendPortInput.getText());
                getPort = Integer.valueOf(getPortInput.getText());
                getFileName = getFNameInput.getText();
                unstableCon = conBox.isSelected();

                Connector c = new Connector(sendIP, sendPort, getPort, getFileName, unstableCon);
                getNumLabel2.textProperty().bind(c.messageProperty());

                //Disable GUI fields
                sendIPInput.disableProperty().bind(c.runningProperty());
                sendPortInput.disableProperty().bind(c.runningProperty());
                getPortInput.disableProperty().bind(c.runningProperty());
                getFNameInput.disableProperty().bind(c.runningProperty());
                confirmButton.disableProperty().bind(c.runningProperty());
                conBox.disableProperty().bind(c.runningProperty());
                
                new Thread(c).start();
                c.setOnFailed(e2-> {
                    popUp(c.getException().getMessage()); 
                });

            }catch(NumberFormatException error){
                popUp("Invalid port number values values.");
            }catch (UnknownHostException error){
                popUp("Invalid Ip address.");
            }catch (Exception error) {
                //System.out.println("Test fail");
                //error.printStackTrace();
            }

        });
        
        
        
        

    }
  
    
}

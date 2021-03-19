import javafx.application.Application;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.control.TextArea;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Scanner;
import javafx.scene.control.ChoiceBox;
import java.net.DatagramPacket;
import java.net.DatagramSocket; 
import java.net.InetAddress;
import java.io.File;
import java.io.FileInputStream;
public class simpleReceiver {
    public static void main(String args[])throws Exception{
        
        
        int sndrPort = 1234;
        DatagramSocket rcvrSocket = new DatagramSocket(4554); 
        DatagramSocket sndrSocket = new DatagramSocket(); 

        DatagramPacket receivedPckt = new DatagramPacket( new byte[1024], 1024);
        rcvrSocket.receive(receivedPckt);

        //create acknowledgement
        byte yByte = (byte) ((Math.random()*50) +1 ); 
        byte rcvdInfo[] = receivedPckt.getData(); 
        byte ack[] = {1 ,yByte, rcvdInfo[0], (byte)(rcvdInfo[1] +1)}; 
        
        //talk back 
        InetAddress sndrIp = InetAddress.getByName( "localhost" );

        DatagramPacket rspndPacket = new DatagramPacket(ack, ack.length, sndrIp, sndrPort); 
        sndrSocket.send(rspndPacket);

        rcvrSocket.receive(receivedPckt);
        rcvdInfo = receivedPckt.getData();
        while (rcvdInfo[0]!= 1 || rcvdInfo[1] != (byte)(yByte + 1 )){
            sndrSocket.send(rspndPacket);

            rcvrSocket.receive(receivedPckt);
            rcvdInfo = receivedPckt.getData();
        }
        System.out.println("HandShake complete"); 

        byte stream[] = new byte[100]; 
        byte lastAck = -1; 

        while(rcvdInfo[0] != -99 || rcvdInfo.length != 1 ) {
            System.out.println("----------------------------------------------------"); 
            rcvrSocket.receive(receivedPckt);
            rcvdInfo = Arrays.copyOfRange(receivedPckt.getData(), 0, receivedPckt.getLength());
            
            System.out.println(String.format("|%s|", new String(rcvdInfo))); 

            System.out.println();
            
            if(rcvdInfo[0 ]== lastAck){

                sndrSocket.send(rspndPacket);
                System.out.printf("RESEND ACK \n");
            }else if (rcvdInfo[0 ]==(byte) 0){

                byte ack0[] = {0}; 
                rspndPacket = new DatagramPacket(ack0, ack0.length, sndrIp, sndrPort);
                sndrSocket.send(rspndPacket);
                System.out.printf("Sent Ack 0\n");
                lastAck = 0; 
            }else if (rcvdInfo[0] == (byte)1){

                byte ack1[] = {1}; 
                rspndPacket = new DatagramPacket(ack1, ack1.length, sndrIp, sndrPort);
                sndrSocket.send(rspndPacket);
                System.out.printf("Sent Ack 1\n");
                lastAck = 1; 
            }

            
        }

        System.out.println("FINISHED");

        sndrSocket.close();
        rcvrSocket.close();
    }



}
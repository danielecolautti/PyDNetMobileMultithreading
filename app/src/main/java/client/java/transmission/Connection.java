package client.java.transmission;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Connection {

        private String indirizzoServer;
        private int serverPort;
        private InetAddress server;
        private Socket sock = null;
        private DataInputStream inSock = null;
        private DataOutputStream outSock = null;
        private ByteBuffer byteBuffer = null;
        private byte [] pixelsByte;
        private static final String TAG = "SocketConnection";

        public Connection (String ip,int port) {
            this.indirizzoServer = ip;
            this.serverPort = port;
        }

        public int startConnection () {
            try {
                server = InetAddress.getByName(indirizzoServer);
                sock = new Socket(server, serverPort);
                inSock = new DataInputStream(sock.getInputStream());
                outSock = new DataOutputStream(sock.getOutputStream());
            } catch(SocketException e) {
                Log.d(TAG,"Start SocketException");
                e.printStackTrace();
                return 1;
            } catch (UnknownHostException e) {
                Log.d(TAG,"Start UnknownHostException");
                e.printStackTrace();
                return 2;
            } catch (IOException e) {
                Log.d(TAG,"Start IOException");
                e.printStackTrace();
                return 3;
            }
            return 0;
        }

        public int stopConnection() {
            if (sock==null)
                return -1;
            try {
                sock.shutdownOutput();
            } catch (IOException e) {
                Log.d(TAG,"Stop IOException");
                e.printStackTrace();
                return 1;
            }
            return 0;
        }

        public synchronized int invia (Object o) {
            if (outSock==null)
                return -1;
            try {
                //Log.d(TAG,o.getClass().getName());
                if (o.getClass().getName().equalsIgnoreCase("java.lang.String"))
                    outSock.writeUTF(o.toString());
                else if (o.getClass().getName().equalsIgnoreCase("java.lang.Long"))
                    outSock.writeLong((long) o);
                else if (o.getClass().getName().equalsIgnoreCase("java.lang.Integer"))
                    outSock.writeInt((int) o);
                else if (o.getClass().getName().equalsIgnoreCase("java.lang.Float"))
                    outSock.writeFloat((float) o);
                else if (o.getClass().getName().equalsIgnoreCase("[I")) {
                    int[] array = (int[]) o;
                    byteBuffer = ByteBuffer.allocateDirect(array.length * Integer.BYTES);
                    pixelsByte = new byte[array.length * Integer.BYTES];
                    for (int i=0; i<array.length; i++)
                        byteBuffer.putInt(array[i]);
                    byteBuffer.rewind();
                    pixelsByte = byteBuffer.array();
                    outSock.write(pixelsByte);
                }
            } catch (IOException e) {
                Log.d(TAG,"Invia IOException");
                e.printStackTrace();
                return 1;
            }
            return 0;
        }

    }

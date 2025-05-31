/**
 * The university of Melbourne
 * COMP90015: Distributed Systems
 * Project 1
 * Author: Yupeng Li
 * Student ID: 1399160
 * Date: 06/04/2023
 */

import java.io.*;
import java.net.*;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.logging.Logger;

public class Client {
    private final String ip;
    private final int port;
    private SocketChannel client;

    private final Logger logger = Logger.getLogger("dictionary_client_log");
    public Client(String ip, int port){
        this.ip = ip;
        this.port = port;
    }

    public boolean ConnectServer(){
        try{
            client = SocketChannel.open();
            client.connect(new InetSocketAddress(ip,port));
            logger.info("Connect ip:" + ip + ", port:" + port + " succeed");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void Close() throws IOException {
        logger.info("Close connect");
        client.close();
    }

    public boolean Query(String word, ArrayList<String> meanings, ErrorCode error_code, StringBuilder error_context) throws IOException, ClassNotFoundException {
        logger.info("Query word: [" + word + "]");
        Request request = new Request();
        request.setOp(Request.QUERY);
        request.setWord(word);
        request.Send(client);

        Response response = new Response();
        response.Recv(client);
        error_code = response.getStatus();
        if (error_code != ErrorCode.SUCCESS) {
            error_context.append(response.getContext());
            return false;
        }
        meanings.addAll(response.getMeanings());
        return true;
    }

    public boolean Add(String word, ArrayList<String> meanings, ErrorCode error_code, StringBuilder error_context) throws IOException, ClassNotFoundException {
        logger.info("Add word: [" + word + "]");
        Request request = new Request(word, Request.ADD);
        request.setMeanings(meanings);
        request.Send(client);
        Response response = new Response();
        response.Recv(client);
        error_code = response.getStatus();
        if (error_code != ErrorCode.SUCCESS) {
            error_context.append(response.getContext());
            return false;
        }
        return true;
    }

    public boolean Remove(String word, ErrorCode error_code, StringBuilder error_context) throws IOException, ClassNotFoundException {
        logger.info("Remove word: [" + word + "]");
        Request request = new Request(word, Request.REMOVE);
        request.Send(client);
        Response response = new Response();
        response.Recv(client);
        error_code = response.getStatus();
        if (error_code != ErrorCode.SUCCESS) {
            error_context.append(response.getContext());
            return false;
        }
        return true;
    }

    public boolean Update(String word, ArrayList<String> meanings, ErrorCode error_code, StringBuilder error_context) throws IOException, ClassNotFoundException {
        logger.info("Update word: [" + word + "]");
        Request request = new Request(word, Request.UPDATE);
        request.setMeanings(meanings);
        request.Send(client);
        Response response = new Response();
        response.Recv(client);
        error_code = response.getStatus();
        if (error_code != ErrorCode.SUCCESS) {
            error_context.append(response.getContext());
            return false;
        }
        return true;
    }

}

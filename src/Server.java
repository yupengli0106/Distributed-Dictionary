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
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

public class Server implements Runnable{
    private final Selector selector;//multiplexer
    private final Logger logger = Logger.getLogger("dictionary_log");
    public Server(int port) throws IOException {
        logger.info("Start Server at port:" + port);
        //implement dictionary
        MyDictionary dictionary = new MyDictionary();
        //open server socket
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        //set non-blocking
        serverSocket.configureBlocking(false);
        //open multiplexer
        selector = Selector.open();
        //register server socket to multiplexer, and set accept event
        SelectionKey key = serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        //bind port
        serverSocket.bind(new InetSocketAddress(port));
        //attach Acceptor to multiplexer
        key.attach(new Acceptor(serverSocket, dictionary));
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Server <port>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        try {
            /**
             * 这是属于启动一个主线程，然后这个主线程里面有一个selector，然后这个selector只绑定了一个 server socket channel
             * 在注册完server socket channel之后，再给这个server socket channel绑定一个或者说添加一个Acceptor，这个Acceptor是一个runnable
             * 然后这个Acceptor对象创建的时候，会创建一个线程池，然后这个线程池里面有10个线程
             * 然后这10个线程都会先创建一个RequestHandler，然后被放到handlers里面，
             * 然后通过executor.execute()方法，把这10个线程都放到线程池里面去运行, 此时真正的开启了10个子线程，执行了10个RequestHandler.run()方法
             * 且此时10个线程都是阻塞的，因为waitChannels是一个空的队列，等待主线程把新的连接放到这个队列里面去
             * 每个线程都有一个RequestHandler，然后各自的RequestHandler里面有一个属于自己的selector
             * 到此server对象创建完毕
             *
             * 接下来执行server.run()，这个run()方法里面会执行selector.select()，然后这个selector会监听所有的事件。
             * selector.select()会阻塞，直到有事件发生，然后selector.selectedKeys()会返回所有发生的事件
             * 然后遍历这些事件，然后调用dispatch()方法，这个方法首先会把这个事件的attachment取出来（attachment就是我们之前绑定的Acceptor）
             * 然后会执行dispatch()方法里面的attachment.run()，也就是执行Acceptor.run()
             * 然后Acceptor.run()里面会执行serverSocket.accept()，这个方法会阻塞，直到有新的连接进来,然后接受这个连接
             * 然后在handlers里面找到一个RequestHandler对象(其实就是一个线程)，再通过nextRegisterThreadIdx去确认分发给哪个子线程(这个nextRegisterThreadIdx是一个循环的)
             * 然后调用这个线程attach的RequestHandler对象的RegisterChannel()方法，把这个新的连接注册到这个线程的waitChannels里面去（这个waitChannels是一个消息队列）
             * 然后会更新nextRegisterThreadIdx，这样下一次再有新的连接进来的时候，就会分发给下一个线程
             * 主线程的run()方法就结束了，然后这个主线程就会阻塞，直到有新的连接进来，然后再执行上面的步骤。
             *
             * 到此，如果有新的连接进来，就会被分发到一个子线程里面去，然后这个子线程就会执行自己的RequestHandler.run()方法
             * RequestHandler.run(）方法会open自己当前线程的selector（一共有10个selector，每个线程一个selector）
             * 然后如果waitChannels不为空，当前线程就会把waitChannels里面的channel都注册到自己的selector里面去
             * 下面就是正常的selector操作，注册感兴趣的事件，设置非阻塞
             * 但是这里使用的是selector.selectNow()，这个方法不会阻塞，如果没有事件发生，就会返回0
             * 如果有事件发生，就会返回发生的事件的数量
             * 然后还是用selector.selectedKeys()获取所有发生的事件，然后遍历这些事件
             * 如果是read事件，就调用ProcessRequest()方法，如果是write事件，就调用ProcessResponse()方法
             *
             */
            Server server = new Server(port);
            server.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //main thread, use multiplexer to listen event
    @Override
    public void run() {
        System.out.println("Server run");
        try {
            while (!Thread.interrupted()) {
                selector.select();//listen event
                Set<SelectionKey> keys = selector.selectedKeys();//get all event
                Iterator<SelectionKey> iterator = keys.iterator();
                while (iterator.hasNext()) {
                    dispatch(iterator.next());//dispatch event to Acceptor after listened a connection event
                    iterator.remove();
                }
                selector.selectNow();
                }
            } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void dispatch(SelectionKey key) throws IOException {
        Runnable attachment = (Runnable) key.attachment();
        attachment.run();//acceptor run()
    }

    public class Acceptor implements Runnable {
        private final int ThreadNum = 10;
        private int nextRegisterThreadIdx = 0;
        private final ServerSocketChannel serverSocket;
        private final ArrayList<RequestHandler> handlers = new ArrayList<>();
        public Acceptor(ServerSocketChannel serverSocket, MyDictionary dictionary) throws IOException {
            this.serverSocket = serverSocket;
            ExecutorService executor = Executors.newFixedThreadPool(ThreadNum);// 已经创建好了10个线程属于server
            for (int i = 0; i < ThreadNum; i++){// 让10个线程开始运行
                handlers.add(new RequestHandler(dictionary));
                //thread pool
                executor.execute(handlers.get(i));//start thread
            }
        }

        @Override
        public void run() {//dispatch connection to thread pool
            System.out.println("Acceptor run");
            try {
                SocketChannel channel = serverSocket.accept();//accept connection
                if (null != channel) {
                    logger.info("Accept new connection at:" + channel.socket().toString());
                    // 主thread把已经注册的channel放到这个requesthandler里面
                    handlers.get(nextRegisterThreadIdx).RegisterChannel(channel);
                    nextRegisterThreadIdx = (nextRegisterThreadIdx+1)%ThreadNum;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class RequestHandler implements Runnable {// line 86
        private final Selector selector;
        private final MyDictionary dictionary;
        private final ConcurrentLinkedQueue<SocketChannel> waitChannels; // queue

        public RequestHandler(MyDictionary dictionary) throws IOException {
            this.dictionary = dictionary;
            selector = Selector.open();
            waitChannels = new ConcurrentLinkedQueue<>();
        }

        public void RegisterChannel(SocketChannel channel) throws IOException {
            // 主thread把channel传过来到waitChannels里面,然后等待子线程来处理到自己的selector里面
            waitChannels.add(channel);// 把channel放到这个队列里面，等待下一次select
        }

        @Override
        public void run() {
            System.out.println("RequestHandler run");

            try {
                while (selector.isOpen()) {
                    //if there is new connection, register it to multiplexer
                    while (!waitChannels.isEmpty()) {
                        SocketChannel channel = waitChannels.poll();// 从waitChannels里面取出一个channel，然后注册到本thread selector里面
                        if (channel == null) continue;
                        logger.info("Listen channel: " + channel.socket().toString());
                        channel.configureBlocking(false);//non-blocking
                        channel.register(selector, SelectionKey.OP_READ);//register channel to multiplexer, and set read event
                    }

                    // is there any write event or read event in established connection
                    selector.selectNow();
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = keys.iterator();

                    //if there is no event, sleep 100us, reduce cpu usage
                    if (!iterator.hasNext()) {
                        Thread.sleep(0, 100000);
                    }

                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();

                        //if there is a read event, process request
                        if (key.isReadable()) {
                            ProcessRequest(key);
                        } else if (key.isWritable()) {
                            ProcessResponse(key);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @SuppressWarnings("unchecked")
        private void ProcessRequest(SelectionKey key) throws IOException, ClassNotFoundException, InterruptedException {
            //get channel
            SocketChannel channel = (SocketChannel) key.channel();
            //get request
            Request request = new Request();
            //if read failed, close channel
            if (!request.Recv(channel)) {
                logger.warning("Disconnect channel: " + channel.socket().toString());
                key.cancel();
                channel.close();
                return;
            }
            Response response = new Response();
            int op = request.getOp();//get operation
            String word = request.getWord();
            if (word.isEmpty() || word.isBlank()) {
                response.setStatus(ErrorCode.INVALID_PARAMETER);
                response.setContext("invalid word");
                logger.warning("Invalid word: " + request.toString());
            }
            logger.info("Receive request for word: [" + word + "]");
            response.setWord(word);

            switch (op) {
                case Request.QUERY -> {
                    logger.info("Query word: [" + word + "] start");
                    ArrayList<String> meanings = new ArrayList<>();
                    ErrorCode error_code = dictionary.Query(word, meanings);
                    if (error_code == ErrorCode.NOT_FOUND) {
                        response.setStatus(ErrorCode.NOT_FOUND);
                        response.setContext("not found");
                        logger.warning("Query word: [" + word + "] not found");
                        break;
                    }
                    for (String meaning : meanings) {
                        logger.info(meaning);
                    }
                    response.setStatus(ErrorCode.SUCCESS);
                    response.setMeanings(meanings);
                    logger.info("Query word: [" + word + "] succeed");
                }
                case Request.UPDATE -> {
                    logger.info("Update word: [" + word + "] start");
                    ArrayList<String> meanings = request.getMeanings();
                    if (meanings == null) {
                        response.setStatus(ErrorCode.INVALID_PARAMETER);
                        response.setContext("empty meanings");
                        logger.warning("Update word: [" + word + "] empty meanings");
                        break;
                    }
                    ErrorCode ret = dictionary.Update(word, meanings);
                    if (ret == ErrorCode.NOT_FOUND) {
                        response.setStatus(ErrorCode.NOT_FOUND);
                        response.setContext("word not found");
                        logger.warning("Update word: [" + word + "] not found");
                        break;
                    }

                    response.setStatus(ErrorCode.SUCCESS);
                    logger.info("Update word: [" + word + "] succeed");
                }
                case Request.ADD -> {
                    logger.info("Add word: [" + word + "] start");
                    ArrayList<String> meanings = request.getMeanings();
                    if (meanings == null) {
                        response.setStatus(ErrorCode.INVALID_PARAMETER);
                        response.setContext("empty meanings");
                        logger.warning("Add word: [" + word + "] empty meanings");
                        break;
                    }
                    ErrorCode ret = dictionary.Add(word, meanings);
                    if (ret == ErrorCode.DUPLICATE) {
                        response.setStatus(ErrorCode.DUPLICATE);
                        response.setContext("Add word: [" + word + "] duplicate");
                        break;
                    }

                    response.setStatus(ret);
                    logger.info("Add word: [" + word + "] succeed");
                }
                case Request.REMOVE -> {
                    logger.info("Remove word: [" + word + "] start");
                    ErrorCode ret = dictionary.Remove(word);
                    if (ret == ErrorCode.NOT_FOUND) {
                        response.setContext("word not found");
                        logger.warning("Remove word: [" + word + "] not found");
                        break;
                    }
                    response.setStatus(ret);
                    logger.info("Remove word: [" + word + "] succeed");
                }
            }
            if (key.attachment() == null) {
                key.attach(new LinkedBlockingQueue<Response>());
            }
            LinkedBlockingQueue<Response> response_queue = (LinkedBlockingQueue<Response>) key.attachment();
            response_queue.put(response);
            key.interestOps(SelectionKey.OP_WRITE);//after process request, set write event
        }

        @SuppressWarnings("unchecked")
        private void ProcessResponse(SelectionKey key) {
            try{
                //get channel
                LinkedBlockingQueue<Response> response_queue = (LinkedBlockingQueue<Response>) key.attachment();
                while (!response_queue.isEmpty()) {
                    Response response = response_queue.poll();
                    logger.info("Send Response for word: [" + response.getWord() + "]");
                    SocketChannel channel = (SocketChannel) key.channel();
                    response.Send(channel);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }
    public class MyDictionary {
        private static final ArrayList<HashMap<String, ArrayList<String>>> dictionary = new ArrayList<>();
        private final ArrayList<ReentrantReadWriteLock> lock = new ArrayList<ReentrantReadWriteLock>();
        private ExecutorService executor;


        public MyDictionary() {
            executor = Executors.newFixedThreadPool(2);
            for (int i = 0; i < 26; i++) {
                //init dictionary, each dictionary is a hashmap(a-z)
                dictionary.add(new HashMap<String, ArrayList<String>>());
                //init lock, each lock is a read write lock
                lock.add(new ReentrantReadWriteLock());
            }
            readFile();
            //start a thread to write to file
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            Thread.sleep(1000*60*5);//write to file every 5 minutes
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        WriteToFile();
                    }
                }
            });

            //add shutdown hook to write to file when shutdown
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        logger.info("Shutdown hook run");
                        WriteToFile();
                    }catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

        }

        private int GetIdx(String word) {
            char c = word.charAt(0);
            int idx = 0;
            if (c >= 'a' && c <= 'z') {
                idx = c - 'a';
            } else {
                idx = c - 'A';
            }
            return idx;
        }

        public ErrorCode Query(String word, ArrayList<String> meanings) {
            int idx = GetIdx(word);
            HashMap<String, ArrayList<String>> hashmap = dictionary.get(idx);
            // read lock can be shared by multiple threads, but write lock can only be held by one thread
            lock.get(idx).readLock().lock();
            ArrayList<String> res = hashmap.get(word);
            if (res == null) {
                lock.get(idx).readLock().unlock();
                return ErrorCode.NOT_FOUND;
            }
            for (String re : res) {
                logger.info("query" + re);
            }
            meanings.addAll(res);
            lock.get(idx).readLock().unlock();
            return ErrorCode.SUCCESS;
        }

        public ErrorCode Add(String word, ArrayList<String> meanings) {
            int idx = GetIdx(word);
            HashMap<String, ArrayList<String>> hashmap = dictionary.get(idx);
            lock.get(idx).readLock().lock();
            if (hashmap.containsKey(word)) {
                lock.get(idx).readLock().unlock();
                return ErrorCode.DUPLICATE;
            }
            lock.get(idx).readLock().unlock();

            lock.get(idx).writeLock().lock();
            hashmap.put(word, meanings);
            lock.get(idx).writeLock().unlock();
            return ErrorCode.SUCCESS;
        }

        public ErrorCode Remove(String word) {
            int idx = GetIdx(word);
            HashMap<String, ArrayList<String>> hashmap = dictionary.get(idx);
            lock.get(idx).readLock().lock();
            if (!hashmap.containsKey(word)) {
                lock.get(idx).readLock().unlock();
                return ErrorCode.NOT_FOUND;
            }
            lock.get(idx).readLock().unlock();

            lock.get(idx).writeLock().lock();
            hashmap.remove(word);
            lock.get(idx).writeLock().unlock();
            return ErrorCode.SUCCESS;
        }

        public ErrorCode Update(String word, ArrayList<String> meanings){
            int idx = GetIdx(word);
            HashMap<String, ArrayList<String>> hashmap = dictionary.get(idx);
            lock.get(idx).readLock().lock();
            if (!hashmap.containsKey(word)) {
                lock.get(idx).readLock().unlock();
                return ErrorCode.NOT_FOUND;
            }
            lock.get(idx).readLock().unlock();

            lock.get(idx).writeLock().lock();
            hashmap.put(word, meanings);
            lock.get(idx).writeLock().unlock();
            return ErrorCode.SUCCESS;
        }

        public void WriteToFile() {//write memory data to disk file
            logger.info("Write to file start");
            ArrayList<HashMap<String, ArrayList<String>>> dictionary = copy();
            //if the file exists, append to it and if not, create a new file
            File file = new File("dictionary.txt");
            try {
                if (file.exists()) {
                    file.delete();
                }
                file.createNewFile();

                FileWriter fw = new FileWriter(file, false);
                BufferedWriter bw = new BufferedWriter(fw);
                for (int i = 0; i < 26; i++) {
                    HashMap<String, ArrayList<String>> hashmap = dictionary.get(i);
                    for (String key : hashmap.keySet()) {
                        bw.write(key + " : ");
                        ArrayList<String> meanings = hashmap.get(key);
                        for (int j = 0; j < meanings.size(); j++) {
                            bw.write(meanings.get(j) + " ");
                        }
                        bw.write("\r\n");
                    }
                }
                fw.flush();
                bw.close();
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void readFile(){//read file to dictionary
            File file = new File("dictionary.txt");
            try {
                if (!file.exists()) {
                    file.createNewFile();
                }
                FileReader fr = new FileReader(file);
                BufferedReader br = new BufferedReader(fr);
                String line = null;
                while ((line = br.readLine()) != null) {
                    String[] words = line.split(" : ");
                    String word = words[0];
                    String[] meanings = words[1].split(" ");
                    ArrayList<String> meaning = new ArrayList<>();
                    for (String meaning1 : meanings) {
                        meaning.add(meaning1);
                    }
                    int idx = GetIdx(word);
                    HashMap<String, ArrayList<String>> hashmap = dictionary.get(idx);
                    hashmap.put(word, meaning);
                }
                br.close();
                fr.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public ArrayList<HashMap<String, ArrayList<String>>> copy(){//deep copy
            //clone dictionary
            ArrayList<HashMap<String, ArrayList<String>>> clone = new ArrayList<>();
            for (int i = 0; i < 26; i++) {
                clone.add(new HashMap<String, ArrayList<String>>());
            }
            for (int i = 0; i < 26; i++) {
                lock.get(i).readLock().lock();
                HashMap<String, ArrayList<String>> hashmap = dictionary.get(i);
                for (String key : hashmap.keySet()) {
                    ArrayList<String> meanings = hashmap.get(key);
                    ArrayList<String> newMeanings = new ArrayList<>();
                    newMeanings.addAll(meanings);
                    clone.get(i).put(key, newMeanings);
                }
                lock.get(i).readLock().unlock();
            }
            return clone;
        }

    }

}

package com.hhp.netty.timeTest.nio.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

public class MultiplexerTimeServer implements Runnable{

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private volatile boolean stop;

    public MultiplexerTimeServer(int port){
        try {
            selector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(port), 1024);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("The time server is start in port : "+port);
        }catch (IOException e){
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void stop(){
        this.stop = true;
    }

    @Override
    public void run() {
        while (!stop){
            try {
                selector.select(1000);
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> it = selectionKeys.iterator();
                SelectionKey key = null;
                while(it.hasNext()){
                    key = it.next();
                    it.remove();
                    try {
                        handleInput(key);
                    }catch (Exception e){
                        if(key!=null){
                            key.cancel();
                            if(key.channel()!=null){
                                key.channel().close();
                            }
                        }
                    }
                }
            }catch (Throwable t){
                t.printStackTrace();
            }
        }
        //多路复用器关闭后，所有注册在上面的channel和pipe等资源都会自动去注册，不需要释放
        if(selector!=null){
            try {
                selector.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private void handleInput(SelectionKey key) throws IOException{
        if(key.isValid()){
            //处理新介入的请求消息
            //接入请求
            if(key.isAcceptable()){
                //Accept the new Connection
                ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
                SocketChannel sc = ssc.accept();
                sc.configureBlocking(false);
                //add the new connection to the selector
                sc.register(selector,SelectionKey.OP_READ);
            }
            //可读
            if(key.isReadable()){
                //Read the data
                SocketChannel sc = (SocketChannel) key.channel();
                ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                int readBytes = sc.read(readBuffer);
                if(readBytes >0){
                    readBuffer.flip();
                    //flip之后，limit与remaining的大小相等
                    byte[] bytes = new byte[readBuffer.remaining()];
                    readBuffer.get(bytes);
                    String body = new String(bytes,"UTF-8");
                    System.out.println("The time server receive order: "+body);
                    String currentTime = "Query time order".equalsIgnoreCase(body)?
                            new Date(System.currentTimeMillis()).toString():"bad order";
                    doWrite(sc, currentTime);
                }else if(readBytes <0){
                    //对端链路关闭
                    key.cancel();
                    sc.close();
                }else {
                    ;   //读到0字节，忽略
                }
            }
        }
    }

    private void doWrite(SocketChannel channel, String response)throws IOException{
        if(response!=null && response.trim().length()>0){
            byte[] bytes = response.getBytes();
            ByteBuffer writeBuffer = ByteBuffer.allocate(bytes.length);
            writeBuffer.put(bytes);
            writeBuffer.flip();
            int rc=0;
            while(rc<writeBuffer.array().length) {
                rc += channel.write(writeBuffer);
            }
        }
    }
}

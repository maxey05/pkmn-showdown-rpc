package com.showdownrpc.dev;

import java.nio.file.Path;

import com.showdownrpc.config.Config;
import com.showdownrpc.showdown.ShowdownClient;

public class ShowdownProbe 
{
    public static void main(String[] args) throws Exception {
        Config config = Config.load(Path.of("config.properties"));
        ShowdownClient client = new ShowdownClient(config);
        client.connect();          // async — returns immediately
        Thread.currentThread().join();   // run until Ctrl+C
    }
}
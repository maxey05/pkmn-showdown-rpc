package com.showdownrpc.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Config 
{
    private final String username;
    private final String password;

    private Config(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public static Config load(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Missing config file: " + path.toAbsolutePath()
                + " — copy config.example.properties and fill it in.");
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        }
        String user = props.getProperty("showdown.username");
        String pass = props.getProperty("showdown.password");
        if (user == null || user.isBlank() || pass == null || pass.isBlank()) {
            throw new IOException("config.properties must set showdown.username and showdown.password");
        }
        return new Config(user.trim(), pass);
    }

    public String username() { return username; }
    public String password() { return password; }

    @Override public String toString() {
        return "Config[username=" + username + ", password=***]";
    }
}
package com.example.springbatchdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.s3")
public class S3Properties {

    private String key;
    private boolean useLocalFile;
    /** When true (S3 only), list objects by prefix and read all matching files. */
    private boolean useFolder;
    /** S3 prefix (folder path) for useFolder mode, e.g. "data/orders/". */
    private String prefix;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public boolean isUseLocalFile() {
        return useLocalFile;
    }

    public void setUseLocalFile(boolean useLocalFile) {
        this.useLocalFile = useLocalFile;
    }

    public boolean isUseFolder() {
        return useFolder;
    }

    public void setUseFolder(boolean useFolder) {
        this.useFolder = useFolder;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}

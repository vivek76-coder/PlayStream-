package com.video.playstream;

import android.net.Uri;

public class VideoModel {
    private String id;
    private String title;
    private Uri uri;
    private String duration;
    private String size;

    public VideoModel(String id, String title, Uri uri, String duration, String size) {
        this.id = id;
        this.title = title;
        this.uri = uri;
        this.duration = duration;
        this.size = size;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Uri getUri() { return uri; }
    public String getDuration() { return duration; }
    public String getSize() { return size; }
}

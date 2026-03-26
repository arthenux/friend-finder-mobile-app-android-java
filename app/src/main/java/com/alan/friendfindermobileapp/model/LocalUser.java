package com.alan.friendfindermobileapp.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalUser {

    private final String id;
    private final String name;
    private final int age;
    private final String city;
    private final String jobTitle;
    private final String headline;
    private final String about;
    private final String interestsCsv;
    private final List<String> photoUris;

    public LocalUser(
            String id,
            String name,
            int age,
            String city,
            String jobTitle,
            String headline,
            String about,
            String interestsCsv,
            List<String> photoUris
    ) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.city = city;
        this.jobTitle = jobTitle;
        this.headline = headline;
        this.about = about;
        this.interestsCsv = interestsCsv;
        this.photoUris = Collections.unmodifiableList(new ArrayList<>(photoUris));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public String getCity() {
        return city;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getHeadline() {
        return headline;
    }

    public String getAbout() {
        return about;
    }

    public String getInterestsCsv() {
        return interestsCsv;
    }

    public List<String> getPhotoUris() {
        return photoUris;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("age", age);
        object.put("city", city);
        object.put("jobTitle", jobTitle);
        object.put("headline", headline);
        object.put("about", about);
        object.put("interestsCsv", interestsCsv);

        JSONArray photos = new JSONArray();
        for (String photoUri : photoUris) {
            photos.put(photoUri);
        }
        object.put("photoUris", photos);
        return object;
    }

    public static LocalUser fromJson(String rawJson) throws JSONException {
        JSONObject object = new JSONObject(rawJson);
        JSONArray photosJson = object.optJSONArray("photoUris");
        List<String> photos = new ArrayList<>();
        if (photosJson != null) {
            for (int i = 0; i < photosJson.length(); i++) {
                photos.add(photosJson.optString(i));
            }
        }

        return new LocalUser(
                object.optString("id"),
                object.optString("name"),
                object.optInt("age"),
                object.optString("city"),
                object.optString("jobTitle"),
                object.optString("headline"),
                object.optString("about"),
                object.optString("interestsCsv"),
                photos
        );
    }
}

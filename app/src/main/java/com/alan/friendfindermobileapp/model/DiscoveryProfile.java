package com.alan.friendfindermobileapp.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DiscoveryProfile {

    private final String id;
    private final String name;
    private final int age;
    private final String city;
    private final String jobTitle;
    private final String headline;
    private final String about;
    private final int distanceMiles;
    private final String initials;
    private final int primaryColor;
    private final int secondaryColor;
    private final String primaryPhotoUrl;
    private final List<String> interests;

    public DiscoveryProfile(
            String id,
            String name,
            int age,
            String city,
            String jobTitle,
            String headline,
            String about,
            int distanceMiles,
            String initials,
            int primaryColor,
            int secondaryColor,
            String primaryPhotoUrl,
            List<String> interests
    ) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.city = city;
        this.jobTitle = jobTitle;
        this.headline = headline;
        this.about = about;
        this.distanceMiles = distanceMiles;
        this.initials = initials;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.primaryPhotoUrl = primaryPhotoUrl;
        this.interests = Collections.unmodifiableList(new ArrayList<>(interests));
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

    public int getDistanceMiles() {
        return distanceMiles;
    }

    public String getInitials() {
        return initials;
    }

    public int getPrimaryColor() {
        return primaryColor;
    }

    public int getSecondaryColor() {
        return secondaryColor;
    }

    public String getPrimaryPhotoUrl() {
        return primaryPhotoUrl;
    }

    public List<String> getInterests() {
        return interests;
    }
}

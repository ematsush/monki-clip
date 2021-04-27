package org.ed.monkiclip.twitch.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder
@Getter
@ToString
public class User {
    private String id;
    private String login;
    private String displayName;
    private String type;
    private String broadcasterType;
    private String description;
    private String profileImageUrl;
    private String offlineImageUrl;
    private int viewCount;
    private String email;
}

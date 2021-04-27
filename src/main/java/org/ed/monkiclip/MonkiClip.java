package org.ed.monkiclip;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.PlaylistItemSnippet;
import com.google.api.services.youtube.model.PlaylistListResponse;
import com.google.api.services.youtube.model.PlaylistSnippet;
import com.google.api.services.youtube.model.PlaylistStatus;
import com.google.api.services.youtube.model.ResourceId;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import com.google.common.collect.Lists;
import org.ed.monkiclip.datastructures.WeeklyDataBase;
import org.ed.monkiclip.twitch.TwitchHelix;
import org.ed.monkiclip.twitch.model.Clip;
import org.ed.monkiclip.twitch.model.User;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.ed.monkiclip.Util.getSundayMorning;

public class MonkiClip {
    private static final WeeklyDataBase<Set<String>> WEEKLY_CLIP_IDS = new WeeklyDataBase<>();

    private static final WeeklyDataBase<Playlist> WEEKLY_PLAYLIST_IDS = new WeeklyDataBase<>();

    private static final Pattern DESC_PATTRN = Pattern.compile("Clip Id:(\\w+)");

    private static TwitchHelix TWITCH_SERVICE;

    private static YouTube YOUTUBE;

    public static void main(String[] args) {
        try {
            Map<String, Object> parsedArgs = parseArgs(args);

            Set<String> requiredArgs = new HashSet<>(asList(
                    "-broadcaster",
                    "-lookBackDays",
                    "-viewThreshold",
                    "-dlDir"
            ));

            if (!parsedArgs.keySet().containsAll(requiredArgs)) {
                requiredArgs.removeAll(parsedArgs.keySet());
                System.err.println("Missing arguments: " + requiredArgs.stream().collect(Collectors.joining(", ")));
                System.exit(1);
            }

            String broadcasterLogin = (String) parsedArgs.get("broadcaster");
            long lookBackDays = (long) parsedArgs.get("lookBackDays");
            int viewThreshold = (int) parsedArgs.get("viewThreshold");
            String dlDir = (String) parsedArgs.get("dlDir");


            System.out.println("Reading Twitch and YouTube credentials.");
            System.out.println("Credential path and names not specificied resorting to default directory at credential/*");
            System.out.println("Searching for youtube_client_secrets.json");

            FileInputStream youtubeCredStream = new FileInputStream("credentials/youtube_client_secret.json");
            FileInputStream twitchCredStream = new FileInputStream("credentials/twitch_client_secret.json");
            YOUTUBE = YouTubeService.initializeYouTube(youtubeCredStream, new File("credentials"), "moonmoon-monki-clip", "moonMonkiClip");
            TWITCH_SERVICE = TwitchHelix.initializeTwitchService(twitchCredStream);

            Optional<User> broadcaster = TWITCH_SERVICE.getUser(broadcasterLogin).stream().findFirst();
            if (!broadcaster.isPresent()) {
                System.out.println(format("Could not find broadcaster %s", broadcasterLogin));
                System.exit(1);
            }
            String broadcasterId = broadcaster.get().getId();

            System.out.println("Starting monki-clip");
            System.out.println(format("Listen to Twitch channel %s", broadcasterLogin));
            System.out.println(format("Scanning up to %d days", lookBackDays));
            System.out.println(format("Accepting clips with views over %d", viewThreshold));

            if (parsedArgs.containsKey("scanDays")) {
                System.out.println("Performing one time scan.");
                System.out.println(format("Scanning for %d days.", parsedArgs.get("scanDays")));
                long scanDays = (Long) parsedArgs.get("scanDays");

                loadDataFromYouTube(LocalDateTime.now().minusDays(scanDays > lookBackDays ? scanDays : lookBackDays), LocalDateTime.now());
                scan(scanDays, broadcasterId, viewThreshold, dlDir);

                System.out.println("Completed one time scan.");
            } else {
                loadDataFromYouTube(LocalDateTime.now().minusDays(lookBackDays), LocalDateTime.now());
            }

            System.out.println("Starting loop.");
            loop(lookBackDays, broadcasterId, viewThreshold, dlDir);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            TWITCH_SERVICE.closeClient();
        }
    }

    private static Map<String, Object> parseArgs(String[] args) {
        Map<String, Object> mappedArgs = new HashMap<>();
        for (int i = 0; i < args.length - 1; i += 2) {
            String descriptor = args[i];
            if (i + 1 >= args.length) {
                System.err.println(format("Bad argument. Descriptor %s is missing a value"));
            }
            String value = args[i + 1];
            switch(descriptor) {
                case "-broadcaster":
                    if (isNullOrEmpty(value)) {
                        System.err.println("Bad argument. Broadcaster identifier is empty.");
                        System.exit(1);
                    }
                    mappedArgs.put("broadcaster", value);
                    break;
                case "-lookBackDays":
                    if (isNullOrEmpty(value)) {
                        System.err.println("Bad argument. Look back day is empty.");
                        System.exit(1);
                    }
                    try {
                        Long lookBackDays = Long.valueOf(value);
                        mappedArgs.put("lookBackDays", lookBackDays);
                    } catch (NumberFormatException e) {
                        System.err.println("Bad argument. Look back day is not a number.");
                        System.exit(1);
                    }
                    break;
                case "-viewThreshold":
                    if (isNullOrEmpty(value)) {
                        System.err.println("Bad Argument. viewThreshold is empty.");
                        System.exit(1);
                    }
                    try {
                        Integer viewThreshold = Integer.valueOf(value);
                        mappedArgs.put("viewThreshold", viewThreshold);
                    } catch (NumberFormatException e) {
                        System.err.println("Bad Argument. viewThreshold is not a number.");
                        System.exit(1);
                    }
                    break;
                case "-dlDir":
                    if (isNullOrEmpty(value)) {
                        System.err.println("Bad Argument. dlDir is empty.");
                        System.exit(1);
                    }
                    mappedArgs.put("dlDir", value);
                    break;
                case "-scanDays":
                    if (isNullOrEmpty(value)) {
                        System.err.println("Bad Argument. scanDays is empty.");
                    }
                    try {
                        Long days = Long.valueOf(value);
                        mappedArgs.put("scanDays", days);
                    } catch (NumberFormatException e) {
                        System.err.println("Bad Argument. scanDays is not a number.");
                        System.exit(1);
                    }
                default:
                    System.err.println("Unknown descriptor " + descriptor);
                    System.exit(1);
            }
        }
        return mappedArgs;
    }

    private static void loop(long days, String broadCasterId, int viewThreshold, String dlPath) throws InterruptedException, IOException {
        while (true) {
            WEEKLY_PLAYLIST_IDS.removeElementsBefore(LocalDateTime.now().minusDays(days + 1));
            WEEKLY_CLIP_IDS.removeElementsBefore(LocalDateTime.now().minusDays(days + 1));

            scan(days, broadCasterId, viewThreshold, dlPath);

            System.out.println("Sleeping...");

            Thread.sleep(3600000);

            System.out.println("Starting new scan");
        }
    }

    private static void scan(long days, String broadCasterId, int viewThreshold, String dlPath) throws IOException {
        System.out.println("Retrieving clips.");
        List<Clip> clipsToDownload = getClips(LocalDateTime.now().minusDays(days), LocalDateTime.now(), broadCasterId)
                .stream().filter(el -> el.getViewCount() >= viewThreshold && !WEEKLY_CLIP_IDS.getWeeklyData(el.getCreatedAt()).contains(el.getId()))
                .collect(Collectors.toList());
        System.out.println(format("Retrieved clips. %d number of clips to download.", clipsToDownload.size()));

        for (Clip clip : clipsToDownload) {
            Optional<Playlist> playlistOptional = getWeeklyPlaylist(clip.getCreatedAt());
            Playlist playlist;
            if (!playlistOptional.isPresent()) {
                playlist = createWeeklyPlaylist(clip.getCreatedAt(), viewThreshold);
            } else {
                playlist = playlistOptional.get();
            }

            downAndUploadClip(clip, dlPath, playlist);
        }

        System.out.println(format("Download and upload complete. Uploaded %d clips.", clipsToDownload.size()));
    }

    private static Optional<Playlist> getWeeklyPlaylist(LocalDateTime thisWeek) throws IOException {
        if (WEEKLY_PLAYLIST_IDS.containsWeeklyData(thisWeek)) {
            return Optional.of(WEEKLY_PLAYLIST_IDS.getWeeklyData(thisWeek));
        }
        LocalDateTime sunday = getSundayMorning(thisWeek);
        String weeklyPlaylistTitle = DateTimeFormatter.ISO_DATE.format(sunday);
        PlaylistListResponse myPlayLists = YOUTUBE.playlists().list("snippet").setMine(true).execute();
        Optional<Playlist> playlistId = myPlayLists.getItems().stream()
                .filter(e -> e.getSnippet().getTitle().startsWith(weeklyPlaylistTitle))
                .findFirst();
        if (playlistId.isPresent()) {
            WEEKLY_PLAYLIST_IDS.putWeeklyData(thisWeek, playlistId.get());
        }
        return playlistId;
    }

    private static List<Clip> getClips(LocalDateTime start, LocalDateTime end, String broadCasterId) throws IOException {
        List<Clip> clips = new ArrayList<>();
        LocalDateTime curr = start;
        while (curr.compareTo(end) <= 0) {
            LocalDateTime limit = curr.plusHours(1);
            limit = limit.compareTo(end) <= 0 ? limit : end;

            clips.addAll(TWITCH_SERVICE.getClips(broadCasterId, curr, limit));

            curr = limit;
        }
        return clips;
    }

    private static void loadDataFromYouTube(LocalDateTime start, LocalDateTime end) throws IOException {
        LocalDateTime curr = start;
        while (end.compareTo(curr) > 0) {
            Optional<Playlist> weeklyPlaylist = getWeeklyPlaylist(start);
            if (weeklyPlaylist.isPresent()) {
                List<String> uploadedClipIds = getUploadedClipIds(weeklyPlaylist.get().getId());
                WEEKLY_CLIP_IDS.putWeeklyData(curr, new HashSet<>(uploadedClipIds));
                WEEKLY_PLAYLIST_IDS.putWeeklyData(curr, weeklyPlaylist.get());
            }
            curr = curr.plusWeeks(1);
        }
    }

    private static List<String> getUploadedClipIds(String playlistId) throws IOException {
        List<String> videoIds = new ArrayList<>();
        PlaylistItemListResponse playlistItems = YOUTUBE.playlistItems()
                .list("snippet,contentDetails")
                .setPlaylistId(playlistId)
                .setMaxResults(50L)
                .execute();
        videoIds.addAll(playlistItems.getItems().stream()
                .map(el -> el.getContentDetails().getVideoId())
                .collect(Collectors.toList()));

        String nextItemToken = playlistItems.getNextPageToken();
        while (nextItemToken != null) {
            PlaylistItemListResponse result = YOUTUBE.playlistItems()
                    .list("snippet,contentDetails")
                    .setPageToken(nextItemToken)
                    .setMaxResults(50L)
                    .execute();
            nextItemToken = result.getNextPageToken();
            videoIds.addAll(result.getItems().stream()
                    .map(el -> el.getContentDetails().getVideoId())
                    .collect(Collectors.toList()));
        }

        List<String> chunkedVideoIds = Lists.partition(videoIds, 50).stream()
                .map(el -> el.stream().collect(Collectors.joining(",")))
                .collect(Collectors.toList());

        List<String> clipId = new ArrayList<>();

        for (String commaSeparatedVideoIds : chunkedVideoIds) {
            VideoListResponse response = YOUTUBE.videos().list("snippet")
                    .setId(commaSeparatedVideoIds)
                    .setMaxResults(50L)
                    .execute();
            List<String> results = response.getItems().stream()
                    .map(el -> {
                        Matcher matcher = DESC_PATTRN.matcher(el.getSnippet().getDescription());
                        if (!matcher.matches()) {
                            throw new IllegalStateException(format("Could not get clip id from description. id: %s", el.getId()));
                        }
                        return matcher.group();
                    })
                    .collect(Collectors.toList());
            clipId.addAll(results);
        }

        return clipId;
    }

    private static Video downAndUploadClip(Clip clip, String dlDir, Playlist playlist) throws IOException {
        String clipFileName = format("%s_%s.mp4", clip.getBroadcasterName(), clip.getId());

        dlDir += "/" + clipFileName;
        File clipFile = new File(dlDir);

        if (WEEKLY_CLIP_IDS.containsWeeklyData(clip.getCreatedAt())
        && WEEKLY_CLIP_IDS.getWeeklyData(clip.getCreatedAt()).contains(clip.getId())) {
            System.out.println(format("Clip % has already been uploaded skipping...", clip.getId()));
            return null;
        }

        System.out.println("============================================================================");
        System.out.println(format("Downloading and uploading clip %s to playlist %s", clip.getId(), playlist.getId()));
        System.out.println("Starting download.");
        TWITCH_SERVICE.downloadClip(clip, new File(dlDir));
        System.out.println("Download complete.");

        if (!clipFile.exists()) {
            System.err.println(format("Downloaded Twitch clip %s but could not find it in directory.", clip.getId()));
            System.exit(1);
        }

        Video uploadMeta = new Video();
        VideoSnippet snippet = new VideoSnippet();
        snippet.setTitle(clip.getTitle());
        snippet.setDescription(createYouTubeDescription(clip));
        uploadMeta.setSnippet(snippet);

        VideoStatus videoStatus = new VideoStatus();
        uploadMeta.setStatus(videoStatus.setPrivacyStatus("public"));

        FileInputStream uploadStream = new FileInputStream(clipFile);
        InputStreamContent videoFile = new InputStreamContent("video/*", uploadStream);
        YouTube.Videos.Insert upload = YOUTUBE.videos().insert("snippet,status", uploadMeta, videoFile);
        MediaHttpUploader uploader = upload.getMediaHttpUploader();

        uploader.setDirectUploadEnabled(false);

        System.out.println("Uploading clip.");
        Video returnVal = upload.execute();
        System.out.println("Upload complete.");

        PlaylistItem playlistItemRequest = createPlaylistItemRequest(returnVal, playlist.getId());

        System.out.println("Adding clip to playlist.");
        YOUTUBE.playlistItems().insert("snippet", playlistItemRequest).execute();
        System.out.println("Added clip.");

        if (!clipFile.delete()) {
            System.err.println("Failed to delete clip mp4");
            System.exit(1);
        }

        if (!WEEKLY_CLIP_IDS.containsWeeklyData(clip.getCreatedAt())) {
            WEEKLY_CLIP_IDS.putWeeklyData(clip.getCreatedAt(), new HashSet<>());
        }
        WEEKLY_CLIP_IDS.getWeeklyData(clip.getCreatedAt()).add(clip.getId());

        System.out.println("============================================================================");

        return returnVal;
    }

    private static Playlist createWeeklyPlaylist(LocalDateTime week, long viewThreshold) throws IOException {
        String isoCurrWeekStart = week.format(DateTimeFormatter.ISO_DATE);
        String isoCurrWeekEnd = week.plusWeeks(1).format(DateTimeFormatter.ISO_DATE);
        String title = format("%s", isoCurrWeekStart, isoCurrWeekEnd);
        String description = format("Clips that were created between %s and %s with more than %d views.", isoCurrWeekStart, isoCurrWeekEnd, viewThreshold);

        Playlist requestBody = createPlaylistRequest(title, description, "public");
        Playlist newPlaylist = YOUTUBE.playlists().insert("snippet", requestBody).execute();
        if (!WEEKLY_PLAYLIST_IDS.containsWeeklyData(week)) {
            WEEKLY_PLAYLIST_IDS.putWeeklyData(week, newPlaylist);
        }
        return newPlaylist;
    }

    private static Playlist createPlaylistRequest(String title, String description, String status) {
        Playlist playlist = new Playlist();
        PlaylistSnippet snippet = new PlaylistSnippet();
        snippet.setTitle(title);
        snippet.setDescription(description);
        playlist.setSnippet(snippet);
        PlaylistStatus playlistStatus = new PlaylistStatus();
        playlistStatus.setPrivacyStatus(status);
        playlist.setStatus(playlistStatus);
        return playlist;
    }

    private static PlaylistItem createPlaylistItemRequest(Video video, String playlistId) {
        PlaylistItem playlistItem = new PlaylistItem();
        PlaylistItemSnippet snippet = new PlaylistItemSnippet();
        snippet.setPlaylistId(playlistId);
        snippet.setPosition(0L);
        ResourceId rId = new ResourceId();
        rId.setPlaylistId(playlistId);
        rId.setVideoId(video.getId());
        rId.setVideoId(video.getKind());
        snippet.setResourceId(rId);
        playlistItem.setSnippet(snippet);
        return playlistItem;
    }

    private static String createYouTubeDescription(Clip clip) {
        StringBuilder sb = new StringBuilder();
        sb.append("Clipped by: ").append(clip.getCreatorName()).append('\n');
        sb.append("Clip id: ").append(clip.getId()).append('\n');
        return sb.toString();
    }

    private static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }
}

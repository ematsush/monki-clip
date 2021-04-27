package org.ed.monkiclip;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import com.google.common.collect.Lists;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;


public class YouTubeService {

    private static final List<String> SCOPES = Lists.newArrayList("https://www.googleapis.com/auth/youtube.upload", "https://www.googleapis.com/auth/youtube.readonly");

    public static void main(String[] args) throws IOException {
        YouTube youTube = initializeYouTube(YouTubeService.class.getResourceAsStream("/google_client_secrets.json"), new File("L:/Home"), "moonmoon-monki-clip", "moonMonkiClip");
    }

    public static YouTube initializeYouTube(InputStream clientSecretsStream, File credentialDirectory, String appName, String dataStoreName) throws IOException {
        final HttpTransport httpTransport = new NetHttpTransport();
        final JsonFactory jsonFactory = new JacksonFactory();

        Reader clientSecretReader = new InputStreamReader(clientSecretsStream);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory, clientSecretReader);


        FileDataStoreFactory fileDataStoreFactory = new FileDataStoreFactory(credentialDirectory);
        DataStore<StoredCredential> datastore = fileDataStoreFactory.getDataStore(dataStoreName);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientSecrets, SCOPES)
                .setCredentialDataStore(datastore)
                .build();

        LocalServerReceiver localReceiver = new LocalServerReceiver.Builder().setPort(8080).build();

        Credential credential = new AuthorizationCodeInstalledApp(flow, localReceiver).authorize("user");

        return new YouTube.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(appName)
                .build();
    }

}

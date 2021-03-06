/*
 * Copyright (c) 2016 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.net.auth.oauth2.google;

import java.awt.Dimension;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.w3c.dom.Document;
import org.w3c.dom.html.HTMLInputElement;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;

import vavi.net.totp.PinGenerator;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker.State;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;


/**
 * AuthorizationCodeInstalledApp.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2016/03/04 umjammer initial version <br>
 */
@PropsEntity(url = "file://${HOME}/.vavifuse/credentials.properties")
public class AuthorizationCodeInstalledApp
        extends com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp {

    /**
     * @param flow authorization code flow
     * @param receiver verification code receiver
     * @throws IOException 
     */
    public AuthorizationCodeInstalledApp(AuthorizationCodeFlow flow, VerificationCodeReceiver receiver) throws IOException {
        super(flow, receiver);
    }

    /** */
    private CountDownLatch latch = new CountDownLatch(1);
    /** */
    private volatile Exception exception;

    /** */
    private String userId;
    @Property(name = "googledrive.password.{0}")
    private transient String password;
    /** */
    private String redirectUrl;
    @Property(name = "googledrive.totpSecret.{0}")
    private String totpSecret;
    
    public Credential authorize(String userId) throws IOException {
        this.userId = userId;

        return super.authorize(userId);
    }    

    /* */
    protected void onAuthorization(AuthorizationCodeRequestUrl authorizationUrl) throws IOException {
        this.redirectUrl = authorizationUrl.getRedirectUri();
        PropsEntity.Util.bind(this, userId);
        
        show(authorizationUrl.build());
    }

    /** */
    private void show(String url) throws IOException {
        exception = null;
        
        SwingUtilities.invokeLater(() -> { openUI(url); });
        
        try { latch.await(); } catch (InterruptedException e) { throw new IllegalStateException(e); }

        closeUI();

        if (exception != null) {
            throw new IllegalStateException(exception);
        }
    }

    /** */
    private JFrame frame;

    /** Create a JFrame with a JButton and a JFXPanel containing the WebView. */
    private void openUI(String url) {
        // This method is invoked on Swing thread
        frame = new JFrame("Don't touch me.");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.getContentPane().setLayout(null); // do the layout manually

        final JFXPanel fxPanel = new JFXPanel();

        frame.add(fxPanel);
        frame.setVisible(true);

        fxPanel.setSize(new Dimension(480, 640));

        frame.getContentPane().setPreferredSize(new Dimension(480, 640));
        frame.pack();

        Platform.runLater(new Runnable() { // this will run initFX as JavaFX-Thread
            @Override
            public void run() {
                initFX(fxPanel, url);
            }
        });
    }

    /** */
    private void closeUI() {
        frame.setVisible(false);
        frame.dispose();
    }

    /** Creates a WebView and fires up */
    private void initFX(JFXPanel fxPanel, String url) {
        Group group = new Group();
        Scene scene = new Scene(group);
        fxPanel.setScene(scene);

        WebView webView = new WebView();

        group.getChildren().add(webView);
        webView.setMinSize(480, 640);
        webView.setMaxSize(480, 640);

        // Obtain the webEngine to navigate
            WebEngine webEngine = webView.getEngine();
            webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<State>() {
                volatile boolean login = false;
                public void changed(ObservableValue<? extends State> ov, State oldState, State newState) {
                    if (newState == State.SUCCEEDED) {
                        String location = webEngine.getLocation();
System.err.println("location: " + location);

                    if (location.startsWith("https://accounts.google.com/ServiceLogin")) {
                        
                        if (!login) { 
                            Document doc = webEngine.getDocument();
        
                            try { Thread.sleep(500); } catch (InterruptedException e) { e.printStackTrace(System.err); }

                            ((HTMLInputElement) doc.getElementById("Email")).setValue(userId);
System.err.println("set email: " + userId);
                            
                            try { Thread.sleep(50); } catch (InterruptedException e) { e.printStackTrace(System.err); }

                            ((HTMLInputElement) doc.getElementById("next")).click();
System.err.println("next");
                                
                                try { Thread.sleep(2000); } catch (InterruptedException e) { e.printStackTrace(System.err); }
    
System.err.println(webEngine.executeScript("document.documentElement.outerHTML"));
                            
//                            ((HTMLInputElement) doc.getElementById("password")).setValue(password);
//System.err.println("set passwd: " + password);

//                            ((HTMLInputElement) doc.getElementById("signIn")).click();
//System.err.println("signin");
                            
                            login = true;

                        } else {
                            exception = new IllegalArgumentException("wrong email or password");
                            latch.countDown();
                        }
                    } else if (location.startsWith("https://accounts.google.com/signin/challenge/totp")) {
                        //
                        if (totpSecret != null && !totpSecret.isEmpty()) {
                            String pin = PinGenerator.computePin(totpSecret, null);
                            Document doc = webEngine.getDocument();

                            try { Thread.sleep(500); } catch (InterruptedException e) { e.printStackTrace(System.err); }

                            ((HTMLInputElement) doc.getElementById("totpPin")).setValue(pin);
System.err.println("pin: " + pin);

                            try { Thread.sleep(50); } catch (InterruptedException e) { e.printStackTrace(System.err); }

                            ((HTMLInputElement) doc.getElementById("submit")).click();
System.err.println("2 step authentication");
                        } else {
System.err.println("no totp secret, enter by yourself");
                        }

                    } else if (location.startsWith(redirectUrl)) {
System.err.println("redirected");
                            latch.countDown();
                        }
                    }
    
System.err.println(ov + ", " + newState + ", " + oldState);
            }
        });
        webEngine.load(url);
    }
}

/* */

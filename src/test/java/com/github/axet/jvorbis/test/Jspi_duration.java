package com.github.axet.jvorbis.test;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;

public class Jspi_duration {

    public static void main(String[] args) {
        try {
            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(new File("src/test/resources/fbodemo1_vorbis.ogg").toURI().toURL());
            System.out.println("Ogg Vorbis audio file duration: " + (long) fileFormat.properties().get("duration") / 1_000_000.0);
        } catch (IOException | UnsupportedAudioFileException e) {
            throw new RuntimeException(e);
        }
    }

}

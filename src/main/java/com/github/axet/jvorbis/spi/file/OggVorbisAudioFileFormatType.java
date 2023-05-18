package com.github.axet.jvorbis.spi.file;

import javax.sound.sampled.AudioFileFormat;

public class OggVorbisAudioFileFormatType extends AudioFileFormat.Type {

    public static final AudioFileFormat.Type OGG_VORBIS = new AudioFileFormat.Type("OggVorbis", "ogg");

    /**
     * Constructs a file type.
     *
     * @param name      the string that names the file type
     * @param extension the string that commonly marks the file type
     *                  without leading dot.
     */
    private OggVorbisAudioFileFormatType( String name, String extension ) {
        super(name, extension);
    }

}

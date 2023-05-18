package com.github.axet.jvorbis.spi.file;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;

import com.github.axet.jvorbis.spi.convert.OggVorbisFormatConversionProvider;
import com.github.axet.libvorbis.JOggVorbis_File;
import com.github.axet.libvorbis.Jvorbis_info;
import org.gagravarr.ogg.OggFile;
import org.gagravarr.ogg.audio.OggAudioStatistics;
import org.gagravarr.vorbis.VorbisFile;

import static com.github.axet.jvorbis.spi.file.OggVorbisAudioFileFormatType.OGG_VORBIS;
import static java.nio.file.StandardOpenOption.READ;

public final class OggVorbisAudioFileReader extends AudioFileReader {

	// there is a real problem: a decoder must process all metadata block. this block can have a huge size.
	private static final int MAX_BUFFER = 1 << 19;// FIXME a metadata block can be 1 << 24.

	@Override
	public AudioFileFormat getAudioFileFormat(final InputStream stream)
			throws UnsupportedAudioFileException, IOException {
		return getAudioFileFormat(stream, true);
	}

	private AudioFileFormat getAudioFileFormat(final InputStream stream, final boolean readDuration)
			throws UnsupportedAudioFileException, IOException {
		stream.mark( MAX_BUFFER );
		final JOggVorbis_File ovf = new JOggVorbis_File();
		if ( ovf.ov_open_callbacks( stream, null, 0, JOggVorbis_File.OV_CALLBACKS_STREAMONLY_NOCLOSE ) < 0 ) {
			ovf.ov_clear();
			stream.reset();
			throw new UnsupportedAudioFileException();
		}
		final Jvorbis_info vi = ovf.ov_info( -1 );
		// can be added properties with additional information
		final AudioFormat af = new AudioFormat( OggVorbisFormatConversionProvider.ENCODING,
				vi.rate, AudioSystem.NOT_SPECIFIED, vi.channels, 1, vi.rate, false );
		Map<String, Object> fileProperties = new HashMap<>();
		if (readDuration) {
			stream.reset();
			VorbisFile vorbisFile = new VorbisFile(new OggFile(stream));
			OggAudioStatistics statistics = new OggAudioStatistics(vorbisFile, vorbisFile);
			statistics.calculate();
			fileProperties.put("duration", (long) (statistics.getDurationSeconds() * 1_000_000L));
		}
		final AudioFileFormat aff = new AudioFileFormat( OGG_VORBIS, af, AudioSystem.NOT_SPECIFIED, fileProperties );
		ovf.ov_clear();
		return aff;
	}

	@Override
	public AudioFileFormat getAudioFileFormat(final URL url)
			throws UnsupportedAudioFileException, IOException {

		InputStream is = null;
		try {
			is = url.openStream();
			return getAudioFileFormat( is );
		} catch(final UnsupportedAudioFileException | IOException e) {
			throw e;
		} finally {
			if( is != null ) {
				try{ is.close(); } catch(final IOException e) {}
			}
		}
	}

	@Override
	public AudioFileFormat getAudioFileFormat(final File file)
			throws UnsupportedAudioFileException, IOException {

		InputStream is = null;
		try {
			is = new BufferedInputStream( Files.newInputStream( file.toPath(), READ ) );
			return getAudioFileFormat( is );
		} catch(final UnsupportedAudioFileException | IOException e) {
			throw e;
		} finally {
			if( is != null ) {
				try{ is.close(); } catch(final IOException e) {}
			}
		}
	}

	@Override
	public AudioInputStream getAudioInputStream(final InputStream stream)
			throws UnsupportedAudioFileException, IOException {
		// doc says: If the input stream does not support this, this method may fail with an IOException.
		// if( ! stream.markSupported() ) stream = new BufferedInputStream( stream, JOggVorbis_File.CHUNKSIZE * 2 );// possible resources leak
		try {
			stream.mark( MAX_BUFFER );
			final AudioFileFormat af = getAudioFileFormat( stream, false );
			stream.reset();// to start read header again
			return new AudioInputStream( stream, af.getFormat(), af.getFrameLength() );
		} catch(final UnsupportedAudioFileException | IOException e) {
			stream.reset();
			throw e;
		}
	}

	@Override
	public AudioInputStream getAudioInputStream(final URL url)
			throws UnsupportedAudioFileException, IOException {

		InputStream is = null;
		try {
			is = url.openStream();
			return getAudioInputStream( is );
		} catch(final UnsupportedAudioFileException | IOException e) {
			if( is != null ) {
				try{ is.close(); } catch(final IOException ie) {}
			}
			throw e;
		}
	}

	@Override
	public AudioInputStream getAudioInputStream(final File file)
			throws UnsupportedAudioFileException, IOException {

		BufferedInputStream is = null;
		try {
			is = new BufferedInputStream( Files.newInputStream( file.toPath(), READ ), MAX_BUFFER );
			return getAudioInputStream( is );
		} catch(final UnsupportedAudioFileException | IOException e) {
			if( is != null ) {
				try{ is.close(); } catch(final IOException ie) {}
			}
			throw e;
		}
	}

}

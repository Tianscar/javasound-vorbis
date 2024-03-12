package com.github.axet.jvorbis.spi.file;

import com.github.axet.jvorbis.spi.convert.OggVorbisFormatConversionProvider;
import com.github.axet.libogg.Jogg_packet;
import com.github.axet.libogg.Jogg_page;
import com.github.axet.libogg.Jogg_stream_state;
import com.github.axet.libogg.Jogg_sync_state;
import com.github.axet.libvorbis.JOggVorbis_File;
import com.github.axet.libvorbis.Jvorbis_block;
import com.github.axet.libvorbis.Jvorbis_comment;
import com.github.axet.libvorbis.Jvorbis_dsp_state;
import com.github.axet.libvorbis.Jvorbis_info;
import com.github.axet.libvorbis.Jvorbis_pcm;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static com.github.axet.jvorbis.spi.file.OggVorbisAudioFileFormatType.OGG_VORBIS;

public final class OggVorbisAudioFileReader extends AudioFileReader {

	// there is a real problem: a decoder must process all metadata block. this block can have a huge size.
	private static final int MAX_BUFFER = 1 << 19;// FIXME a metadata block can be 1 << 24.

	@Override
	public AudioFileFormat getAudioFileFormat(final InputStream stream)
			throws UnsupportedAudioFileException, IOException {

		final JOggVorbis_File ovf = new JOggVorbis_File();
		if( ovf.ov_open_callbacks( stream, null, 0, JOggVorbis_File.OV_CALLBACKS_STREAMONLY_NOCLOSE ) < 0 ) {
			ovf.ov_clear();
			throw new UnsupportedAudioFileException();
		}
		final Jvorbis_info vi = ovf.ov_info( -1 );
		// can be added properties with additional information
		final AudioFormat af = new AudioFormat( OggVorbisFormatConversionProvider.ENCODING,
				vi.rate, AudioSystem.NOT_SPECIFIED, vi.channels, 1, vi.rate, false );
		final AudioFileFormat aff = new AudioFileFormat( OGG_VORBIS, af, AudioSystem.NOT_SPECIFIED );
		ovf.ov_clear();
		return aff;
	}

	@Override
	public AudioFileFormat getAudioFileFormat(final URL url)
			throws UnsupportedAudioFileException, IOException {

		final Jogg_sync_state   oy = new Jogg_sync_state(); /* sync and verify incoming physical bitstream */
		final Jogg_stream_state os = new Jogg_stream_state(); /* take physical pages, weld into a logical
																stream of packets */
		final Jogg_page         og = new Jogg_page(); /* one Ogg bitstream page. Vorbis packets are inside */
		final Jogg_packet       op = new Jogg_packet(); /* one raw packet of data for decode */

		final Jvorbis_info      vi = new Jvorbis_info(); /* struct that stores all the static vorbis bitstream
							  								settings */
		final Jvorbis_comment   vc = new Jvorbis_comment(); /* struct that stores all the bitstream user comments */
		final Jvorbis_dsp_state vd = new Jvorbis_dsp_state(); /* central working state for the packet->PCM decoder */
		final Jvorbis_block     vb = new Jvorbis_block(); /* local working space for packet->PCM decode */

		int buffer;
		int bytes;

		AudioFormat af = null;
		long samples = 0;

		try ( InputStream stream = url.openStream() ) {

			/********** Decode setup ************/

			oy.ogg_sync_init(); /* Now we can read pages */

			while ( true ) { /* we repeat if the bitstream is chained */
				boolean eos = false;
				int i;

				/* grab some data at the head of the stream. We want the first page
				   (which is guaranteed to be small and only contain the Vorbis
				   stream initial header) We need the first page to get the stream
				   serialno. */

				/* submit a 4k block to libvorbis' Ogg layer */
				buffer = oy.ogg_sync_buffer( 4096 );
				bytes = stream.read( oy.data, buffer, 4096 );
				if( bytes < 0 ) bytes = 0;// fread returns 0 if eos
				oy.ogg_sync_wrote( bytes );

				/* Get the first page. */
				if ( oy.ogg_sync_pageout( og ) != 1 ) {
					/* have we simply run out of data?  If so, we're done. */
					if( bytes < 4096 ) break;

					/* error case.  Must not be Vorbis data */

					vb.vorbis_block_clear();
					vd.vorbis_dsp_clear();
					os.ogg_stream_clear();
					vc.vorbis_comment_clear();
					vi.vorbis_info_clear();  /* must be called last */
					oy.ogg_sync_clear();

					throw new UnsupportedAudioFileException("Input does not appear to be an Ogg bitstream.");
				}

				/* Get the serial number and set up the rest of decode. */
				/* serialno first; use it to set up a logical stream */
				os.ogg_stream_init( og.ogg_page_serialno() );

				/* extract the initial header from the first page and verify that the
				   Ogg bitstream is in fact Vorbis data */

				/* I handle the initial header first instead of just having the code
				   read all three Vorbis headers at once because reading the initial
				   header is an easy way to identify a Vorbis bitstream and it's
				   useful to see that functionality seperated out. */

				vi.vorbis_info_init();
				vc.vorbis_comment_init();
				if ( os.ogg_stream_pagein( og ) < 0 ) {
					/* error; stream version mismatch perhaps */

					vb.vorbis_block_clear();
					vd.vorbis_dsp_clear();
					os.ogg_stream_clear();
					vc.vorbis_comment_clear();
					vi.vorbis_info_clear();  /* must be called last */
					oy.ogg_sync_clear();

					throw new UnsupportedAudioFileException("Error reading first page of Ogg bitstream data.");
				}

				if( os.ogg_stream_packetout( op ) != 1 ) {
					/* no page? must not be vorbis */

					vb.vorbis_block_clear();
					vd.vorbis_dsp_clear();
					os.ogg_stream_clear();
					vc.vorbis_comment_clear();
					vi.vorbis_info_clear();  /* must be called last */
					oy.ogg_sync_clear();

					throw new UnsupportedAudioFileException("Error reading initial header packet.");
				}

				if ( Jvorbis_info.vorbis_synthesis_headerin( vi, vc, op ) < 0 ) {
					/* error case; not a vorbis header */

					vb.vorbis_block_clear();
					vd.vorbis_dsp_clear();
					os.ogg_stream_clear();
					vc.vorbis_comment_clear();
					vi.vorbis_info_clear();  /* must be called last */
					oy.ogg_sync_clear();

					throw new UnsupportedAudioFileException("This Ogg bitstream does not contain Vorbis audio data.");
				}

				/* At this point, we're sure we're Vorbis. We've set up the logical
				   (Ogg) bitstream decoder. Get the comment and codebook headers and
				   set up the Vorbis decoder */

				/* The next two packets in order are the comment and codebook headers.
				   They're likely large and may span multiple pages. Thus we read
				   and submit data until we get our two packets, watching that no
				   pages are missing. If a page is missing, error out; losing a
				   header page is the only place where missing data is fatal. */

				i = 0;
				while ( i < 2 ) {
					while ( i < 2 ) {
						int result = oy.ogg_sync_pageout( og );
						if ( result == 0 ) break; /* Need more data */
						/* Don't complain about missing or corrupt data yet. We'll
						   catch it at the packet output phase */
						if ( result == 1 ) {
							os.ogg_stream_pagein( og ); /* we can ignore any errors here
														 as they'll also become apparent
														 at packetout */
							while ( i < 2 ) {
								result = os.ogg_stream_packetout( op );
								if ( result == 0 ) break;
								if ( result < 0 ) {
									/* Uh oh; data at some point was corrupted or missing!
									 We can't tolerate that in a header.  Die. */

									vb.vorbis_block_clear();
									vd.vorbis_dsp_clear();
									os.ogg_stream_clear();
									vc.vorbis_comment_clear();
									vi.vorbis_info_clear();  /* must be called last */
									oy.ogg_sync_clear();

									throw new UnsupportedAudioFileException("Corrupt secondary header.");
								}
								result = Jvorbis_info.vorbis_synthesis_headerin( vi, vc, op );
								if ( result < 0 ) {

									vb.vorbis_block_clear();
									vd.vorbis_dsp_clear();
									os.ogg_stream_clear();
									vc.vorbis_comment_clear();
									vi.vorbis_info_clear();  /* must be called last */
									oy.ogg_sync_clear();

									throw new UnsupportedAudioFileException("Corrupt secondary header.");
								}
								i ++;
							}
						}
					}
					/* no harm in not checking before adding more */
					buffer = oy.ogg_sync_buffer( 4096 );
					bytes = stream.read( oy.data, buffer, 4096 );
					if ( bytes < 0 ) bytes = 0;// java: fread returns 0 if eos
					if ( bytes == 0 && i < 2 ) {

						vb.vorbis_block_clear();
						vd.vorbis_dsp_clear();
						os.ogg_stream_clear();
						vc.vorbis_comment_clear();
						vi.vorbis_info_clear();  /* must be called last */
						oy.ogg_sync_clear();

						throw new UnsupportedAudioFileException("End of file before finding all Vorbis headers!");
					}
					oy.ogg_sync_wrote( bytes );
				}

				/* Throw the comments plus a few lines about the bitstream we're
				   decoding */
				/*
				{
					final String[] user_comments = vc.user_comments;
					for( i = 0; i < user_comments.length; i++ ) {
						System.err.println( user_comments[i] );
					}
					System.err.printf("\nBitstream is %d channel, %dHz\n", vi.channels, vi.rate );
					System.err.printf("Encoded by: %s\n\n", vc.vendor );
				}

				 */

				/* OK, got and parsed all three headers. Initialize the Vorbis
				   packet->PCM decoder. */
				if ( ! vd.vorbis_synthesis_init( vi ) ) { /* central decode state */
					vd.vorbis_block_init( vb );        /* local state for most of the decode
														  so multiple block decodes can
														  proceed in parallel. We could init
														  multiple vorbis_block structures
														  for vd here */

					/* The rest is just a straight decode loop until end of stream */
					while ( ! eos ) {
						while ( ! eos ) {
							int result = oy.ogg_sync_pageout( og );
							if ( result == 0 ) break; /* need more data */
							if ( result < 0 ) { /* missing or corrupt data at this page position */
								//System.err.print("Corrupt or missing data in bitstream; continuing...\n");
							} else {
								os.ogg_stream_pagein( og ); /* can safely ignore errors at
																			this point */
								while ( true ) {
									result = os.ogg_stream_packetout( op );

									if ( result == 0 ) break; /* need more data */
									if ( result < 0 ) { /* missing or corrupt data at this page position */
										/* no reason to complain; already complained above */
									} else {
										/* we have a packet.  Decode it */
										Jvorbis_pcm vpcm;

										if ( vb.vorbis_synthesis( op ) == 0 ) /* test for success! */
											vd.vorbis_synthesis_blockin( vb );

										while ( (vpcm = vd.vorbis_synthesis_pcmout( false ) ).samples > 0 ) {

											final int bout = vpcm.samples;
											samples += bout;

											vd.vorbis_synthesis_read( bout ); /* tell libvorbis how
																			  many samples we
																			  actually consumed */
										}
									}
								}
								if ( og.ogg_page_eos() ) eos = true;
							}
						}
						if ( ! eos ) {
							buffer = oy.ogg_sync_buffer( 4096 );
							bytes = stream.read( oy.data, buffer, 4096 );
							if( bytes < 0 ) bytes = 0;// java: fread returns 0 if eos
							oy.ogg_sync_wrote( bytes );
							if( bytes <= 0 ) eos = true;
						}
					}

					/* ogg_page and ogg_packet structs always point to storage in
					 libvorbis.  They're never freed or manipulated directly */

					vb.vorbis_block_clear();
					vd.vorbis_dsp_clear();
				} else {

					vb.vorbis_block_clear();
					vd.vorbis_dsp_clear();
					os.ogg_stream_clear();
					vc.vorbis_comment_clear();
					vi.vorbis_info_clear();  /* must be called last */
					oy.ogg_sync_clear();

					throw new UnsupportedAudioFileException("Corrupt header during initialization.");
				}

				/* clean up this logical bitstream; before exit we see if we're
				   followed by another [chained] */

				if (af == null) {
					af = new AudioFormat( OggVorbisFormatConversionProvider.ENCODING,
							vi.rate, AudioSystem.NOT_SPECIFIED, vi.channels, 1, vi.rate, false );
				}

				os.ogg_stream_clear();
				vc.vorbis_comment_clear();
				vi.vorbis_info_clear();  /* must be called last */
			}

			/* OK, clean up the framer */
			oy.ogg_sync_clear();

		}

		// can be added properties with additional information

		Map<String, Object> props = new HashMap<>();
		props.put("duration", (long) (samples / ((double) af.getFrameRate()) * 1_000_000L) );

		final AudioFileFormat aff = new AudioFileFormat( OGG_VORBIS, af, AudioSystem.NOT_SPECIFIED, props );
		return aff;

	}

	@Override
	public AudioFileFormat getAudioFileFormat(final File file)
			throws UnsupportedAudioFileException, IOException {

		try ( InputStream stream = JOggVorbis_File.ov_open(file.getAbsolutePath()) ) {

			final JOggVorbis_File ovf = new JOggVorbis_File();

			if( ovf.ov_open_callbacks( stream, null, 0, JOggVorbis_File.OV_CALLBACKS_NOCLOSE ) < 0 ) {
				ovf.ov_clear();
				throw new UnsupportedAudioFileException();
			}
			final Jvorbis_info vi = ovf.ov_info( -1 );
			// can be added properties with additional information
			final AudioFormat af = new AudioFormat( OggVorbisFormatConversionProvider.ENCODING,
					vi.rate, AudioSystem.NOT_SPECIFIED, vi.channels, 1, vi.rate, false );

			Map<String, Object> props = new HashMap<>();
			props.put("duration", (long) ( ovf.ov_time_total(-1) * 1_000_000L) );

			final AudioFileFormat aff = new AudioFileFormat( OGG_VORBIS, af, AudioSystem.NOT_SPECIFIED, props );
			ovf.ov_clear();
			return aff;
		}

	}

	@Override
	public AudioInputStream getAudioInputStream(final InputStream stream)
			throws UnsupportedAudioFileException, IOException {
		// doc says: If the input stream does not support this, this method may fail with an IOException.
		// if( ! stream.markSupported() ) stream = new BufferedInputStream( stream, JOggVorbis_File.CHUNKSIZE * 2 );// possible resources leak
		try {
			stream.mark( MAX_BUFFER );
			final AudioFileFormat af = getAudioFileFormat( stream );
			stream.reset();// to start read header again
			return new AudioInputStream( stream, af.getFormat(), af.getFrameLength() );
		} catch(final UnsupportedAudioFileException e) {
			stream.reset();
			throw e;
		} catch(final IOException e) {
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
		} catch(final UnsupportedAudioFileException e) {
			if( is != null ) {
				try{ is.close(); } catch(final IOException ie) {}
			}
			throw e;
		} catch(final IOException e) {
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
			is = new BufferedInputStream( new FileInputStream( file ), MAX_BUFFER );
			return getAudioInputStream( is );
		} catch(final UnsupportedAudioFileException e) {
			if( is != null ) {
				try{ is.close(); } catch(final IOException ie) {}
			}
			throw e;
		} catch(final IOException e) {
			if( is != null ) {
				try{ is.close(); } catch(final IOException ie) {}
			}
			throw e;
		}
	}

}

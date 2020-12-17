/**
 * @author VISTALL
 * @since 16/12/2020
 */
module jvorbis {
	requires transitive java.desktop;

	requires transitive jogg;

	exports com.github.axet.libvorbis;
	exports com.github.axet.libvorbis.books.coupled;
	exports com.github.axet.libvorbis.books.floor;
	exports com.github.axet.libvorbis.books.uncoupled;
	exports com.github.axet.libvorbis.modes;

	provides javax.sound.sampled.spi.AudioFileReader with com.github.axet.jvorbis.spi.file.OggVorbisAudioFileReader;
	provides javax.sound.sampled.spi.AudioFileWriter with com.github.axet.jvorbis.spi.file.OggVorbisAudioFileWriter;
	provides javax.sound.sampled.spi.FormatConversionProvider with com.github.axet.jvorbis.spi.convert.OggVorbisFormatConversionProvider;

}
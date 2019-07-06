package com.github.axet.libvorbis;

final class Jvorbis_info_floor0 extends Jvorbis_info_floor {
	int order = 0;
	int rate  = 0;
	int barkmap = 0;

	int ampbits = 0;
	int ampdB   = 0;

	/** <= 16 */
	int numbooks = 0;
	final int[] books = new int[16];

	/** encode-only config setting hacks for libvorbis */
	float lessthan = 0.0f;
	/** encode-only config setting hacks for libvorbis */
	float greaterthan = 0.0f;
	/*
	private void clear() {
		order = 0;
		rate = 0;
		barkmap = 0;
		ampbits = 0;
		ampdB = 0;
		numbooks = 0;
		Arrays.fill( books, 0 );

		lessthan = 0.0f;
		greaterthan = 0.0f;
	}*/
}

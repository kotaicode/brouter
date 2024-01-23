package btools.codec;

import btools.util.BitCoderContext;

/**
 * Container for some re-usable databuffers for the decoder
 */
public final class DataBuffers {
  public byte[] iobuffer;
  public byte[] tagbuf1 = new byte[256];
  public BitCoderContext bctx1 = new BitCoderContext(tagbuf1);
  public int[] ibuf2 = new int[2048];

  public DataBuffers() {
    this(new byte[102400]);
  }

  /**
   * construct a set of databuffers except
   * for 'iobuffer', where the given array is used
   */
  public DataBuffers(byte[] iobuffer) {
    this.iobuffer = iobuffer;
  }

}

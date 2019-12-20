//  ************************************************************************
//  Copyright 2019 eBay Inc.
//  Author/Developer(s): Simon Fell
//
//  For further information, see also: Fourny, Ghislain. (2015). decimalInfinite: All Decimals In
//  Bits, No Loss, Same Order, Simple.
//
//  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
//  except in compliance with the License. You may obtain a copy of the License at
//  https://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software distributed under the
//  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
//  express or implied. See the License for the specific language governing permissions and
//  limitations under the License.
//  ************************************************************************

package com.ebay.data.encoding;

import java.io.IOException;
import java.io.OutputStream;
import java.util.NoSuchElementException;
import javax.annotation.ParametersAreNonnullByDefault;

/** Bits helps generate or read binary encoded values on a bit by bit basis. */
@ParametersAreNonnullByDefault
public class Bits {

  /**
   * contains 1's in all positions in the byte after the index, e.g. [0] = 11111111, [1] = 01111111,
   * [2] = 00111111.
   */
  private static final byte[] leadingZeroMasks;

  /**
   * contains 0's starting at a position and going through the rest of the byte, e.g. [1]=10000000,
   * [2]=11000000 etc. These are used to create a mask for a range of bits within a byte. If
   * position is 0, then we assume the mask is for the prior byte (as end is always exclusive part
   * of the range), and so [0] = 11111111.
   */
  private static final byte[] trailingZeroMasks;

  /**
   * contains a 1 in the relevant bit position, as numbered by the class (i.e. 0 is MSB). e.g.
   * [0]=1000000, [1]=0100000, [2]=00100000, etc.
   */
  private static final byte[] singleBits;

  static {
    leadingZeroMasks = new byte[8];
    trailingZeroMasks = new byte[8];
    singleBits = new byte[8];
    int mask = 0xFF;
    for (int p = 0; p < 8; p++) {
      leadingZeroMasks[p] = (byte) mask;
      trailingZeroMasks[p] = (byte) (~mask);
      singleBits[p] = (byte) (1 << (8 - p - 1));
      mask = mask >>> 1;
    }
    trailingZeroMasks[0] = (byte) 0xFF;
  }

  /** Constructs a new empty Bits with a default capacity. */
  Bits() {
    this(4 * 8);
  }

  /** Constructs a new empty Bits with at least the specified capacity (in bits). */
  Bits(int capacityInBits) {
    if (capacityInBits < 8) {
      throw new IllegalArgumentException("Initial capacity must be at least 8 bits");
    }
    this.data = new byte[(capacityInBits + 7) / 8];
    this.pos = 0;
  }

  /** Constructs a Bits that is initialized with the bits from the provided byte array. */
  Bits(byte[] encoded) {
    this.data = encoded;
    this.pos = encoded.length * 8;
  }

  private byte[] data;
  private int pos;

  /**
   * The current capacity, in bits.
   *
   * @return the total number of bits that can be stored without another allocation.
   */
  int capacity() {
    return data.length * 8;
  }

  /**
   * Number of bits accumulated.
   *
   * @return the total number of bits that have been accumulated/written.
   */
  public int length() {
    return pos;
  }

  /** The number of bytes needed to store the current value. */
  public int byteLength() {
    return (pos + 7) / 8;
  }

  /** Ensure that the backing array has space to store numNewBits additional bits. */
  private void ensureSpaceFor(int numNewBits) {
    // do we need to expand the backing array?
    if (pos + numNewBits - 1 >= capacity()) {
      byte[] expanded = new byte[(pos + numNewBits) * 2 / 8];
      System.arraycopy(data, 0, expanded, 0, data.length);
      this.data = expanded;
    }
  }

  /** Sets the next bits to the supplied values. false=0, true=1 */
  Bits setNext(boolean... values) {
    ensureSpaceFor(values.length);
    for (boolean b : values) {
      setNextBit(b);
    }
    return this;
  }

  /** Sets the next bits to the supplied values, only 0 or 1 are allowed as values). */
  Bits setNext(int... items) {
    ensureSpaceFor(items.length);
    for (int b : items) {
      if (b < 0 || b > 1) {
        throw new IllegalArgumentException("Provided value can only be 0 or 1, but was " + b);
      }
      setNextBit(b == 1);
    }
    return this;
  }

  /** Sets the next bit, assumes ensureSpaceFor has already been called. */
  private void setNextBit(boolean setToOne) {
    if (setToOne) {
      data[pos / 8] |= singleBits[pos & 0x07];
    }
    pos++;
  }

  /** Sets the next N bits to the same value. */
  Bits setNextN(int n, boolean one) {
    if (n < 1) {
      throw new IllegalArgumentException("n must be greater than 0");
    }
    ensureSpaceFor(n);
    if (one) {
      applyToBytes(pos, pos + n, (byteVal, mask) -> (byte) (byteVal | mask));
    }
    pos += n;
    return this;
  }

  /** Sets the next N bits from the lower n bits of src. */
  Bits setNextNFrom(int n, long src) {
    if (n < 1 || n > 63) {
      throw new IllegalArgumentException("n out of range");
    }
    ensureSpaceFor(n);
    while (n > 0) {
      int thisN = Math.min(8 - (pos & 0x07), n);
      setNextNFrom(thisN, src, n);
      n -= thisN;
    }
    return this;
  }

  /**
   * Adds N bits from src starting at bitOffsetInSrc (starting from right). Assumes N fits in the
   * current destination byte.
   */
  private void setNextNFrom(int n, long src, int bitOffsetInSrc) {
    int destOffset = 8 - (pos & 0x07);
    int move = bitOffsetInSrc - destOffset;
    if (move > 0) {
      src = src >>> move;
    } else {
      src = src << -move;
    }
    int m = leadingZeroMasks[pos & 0x07] & trailingZeroMasks[(pos + n) & 0x07];
    data[pos / 8] |= (m & src);
    pos += n;
  }

  /** Flips the state of each bit between start (inclusive) and end (exclusive). */
  void flip(int start, int end) {
    if (start < 0) {
      throw new IllegalArgumentException("Start must be 0 or greater");
    }
    if (end <= start) {
      throw new IllegalArgumentException("end must be greater than start");
    }
    if (end > pos) {
      throw new IllegalArgumentException("end index out of range");
    }
    // XOR with the mask to flip all the bits in byteVal where the mask bit is 1.
    applyToBytes(start, end, (byteVal, mask) -> (byte) (byteVal ^ mask));
  }

  @FunctionalInterface
  private static interface ByteApply {
    /**
     * Return the new byte for this value 'byteVal'. Will be called for each byte that contains at
     * least 1 bit in the bits to be processed. This is used to process bits in byte chunks rather
     * than bit at a time.
     *
     * @param byteVal The existing value
     * @param mask The mask has a bit set for each bit in byteVal that should be processed.
     * @return the updated value for this byte.
     */
    byte apply(byte byteVal, int mask);
  }

  /**
   * Apply a modifier function to the bytes that contain the supplied range of bits, start-end
   * (inclusive,exclusive) The function will be called for each byte along with a mask where a 1
   * indicates that bit is within the range.
   *
   * @param start the starting bit position. (inclusive)
   * @param end the ending bit position (exclusive)
   * @param fn the function to apply to the bytes.
   */
  private void applyToBytes(int start, int end, ByteApply fn) {
    int startByteIdx = start / 8;
    // end is exclusive, but endByteIdx is the index of the last byte we need to touch
    int endByteIdx = (end - 1) / 8;
    for (int idx = startByteIdx; idx <= endByteIdx; idx++) {
      byte mask = (byte) 0xFF;
      if (idx == startByteIdx) {
        mask = leadingZeroMasks[start & 0x07];
      }
      if (idx == endByteIdx) {
        // we AND the mask here to handle the case where start & end are
        // at the same byte index.
        mask &= trailingZeroMasks[end & 0x07];
      }
      data[idx] = fn.apply(data[idx], mask);
    }
  }

  /**
   * Create a byte array representation of the bits.
   *
   * @return A byte array copy of the current value. The last byte will be padded with 0's as
   *     needed. Bit 0 is the MSB in byte 0, bit 7 is the LSB in byte 0, bit 8 is the MSB in byte 1
   *     etc.
   */
  public byte[] toByteArray() {
    byte[] r = new byte[byteLength()];
    System.arraycopy(data, 0, r, 0, r.length);
    return r;
  }

  /**
   * Writes the value to the supplied array, starting at the indicated offset.
   *
   * @param dest The array to write the value to.
   * @param offset The offset into the supplied array to start writing the value at.
   * @throws IllegalArgumentException if the supplied buffer is not large enough to contain the
   *     value.
   */
  public void writeTo(byte[] dest, int offset) {
    int len = byteLength();
    if (len + offset > dest.length) {
      throw new IllegalArgumentException("The supplied destination array is not large enough");
    }
    System.arraycopy(data, 0, dest, offset, len);
  }

  /**
   * Writes the value to the supplied stream.
   *
   * @throws IOException If the write to the stream fails.
   */
  public void writeTo(OutputStream os) throws IOException {
    os.write(data, 0, byteLength());
  }

  /** returns true if the bit at the indicated position is set. */
  boolean isSet(int posn) {
    if (posn < 0 || posn >= pos) {
      throw new IndexOutOfBoundsException(
          String.format("Position %d is out of bounds of %d", posn, pos));
    }
    return (data[posn / 8] & singleBits[posn & 0x07]) != 0;
  }

  Iterator iterator() {
    return new Iterator();
  }

  /**
   * Iterates over the current bit values. This is a view over the underlying data, so if the Bits
   * is modified, that'll be reflected in the iterator.
   */
  class Iterator {

    private int itPos = 0;

    boolean hasNext() {
      return this.itPos < pos;
    }

    int position() {
      return itPos;
    }

    int remaining() {
      return pos - itPos;
    }

    boolean next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return isSet(itPos++);
    }

    /**
     * returns a long by decoding the next N bits into the lower bits of a long, such that the Nth
     * bit is the LSB of the result.
     *
     * <p>e.g. if the next values from the iterator are 1,0,1 then nextN(3) would return 5.
     */
    long nextN(int n) {
      if (n <= 0 || n > 64) {
        throw new IllegalArgumentException("N out of range: " + n);
      }
      long v = 0;
      for (int i = 0; i < n; i++) {
        v = v << 1;
        if (next()) {
          v = v | 1;
        }
      }
      return v;
    }
  }

  /**
   * Returns a string representation of the value in binary. Bits are split into groups of 4 for
   * readability.
   */
  public String toBitString() {
    StringBuilder r = new StringBuilder(pos + pos / 4);
    Iterator it = iterator();
    while (it.hasNext()) {
      int p = it.position();
      if ((p % 4 == 0) && (p > 0)) {
        r.append(' ');
      }
      r.append(it.next() ? '1' : '0');
    }
    return r.toString();
  }
}

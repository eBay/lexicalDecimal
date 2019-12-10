package com.ebay.data.encoding;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class BitsTest {

  @Test
  void testEmpty() {
    Bits e = new Bits();
    assertEquals(0, e.toByteArray().length);
    assertEquals("", e.toBitString());
    assertEquals(0, e.length());
    assertEquals(0, e.byteLength());
    assertTrue(e.capacity() > 0);
  }

  @Test
  void testInitialCapacity() {
    Bits b = new Bits(64);
    assertEquals(64, b.capacity());
    assertEquals(0, b.length());
    assertThrows(IllegalArgumentException.class, () -> new Bits(3));
    assertThrows(IllegalArgumentException.class, () -> new Bits(-1));

    assertEquals(8, new Bits(8).capacity());
    assertEquals(16, new Bits(9).capacity());
    assertEquals(16, new Bits(15).capacity());
    assertEquals(16, new Bits(16).capacity());
  }

  @Test
  void testFromExisting() {
    byte[] d = {(byte) 0xFF, 0, (byte) 0xFF};
    Bits b = new Bits(d);
    assertArrayEquals(d, b.toByteArray());
    assertEquals("1111 1111 0000 0000 1111 1111", b.toBitString());
    assertEquals(24, b.length());
    assertEquals(24, b.capacity());
    b.setNext(1);
    assertEquals("1111 1111 0000 0000 1111 1111 1", b.toBitString());
    assertEquals(25, b.length());
    assertEquals(48, b.capacity());
  }

  @Test
  void testLength() {
    Bits b = new Bits();
    assertEquals(0, b.length());
    assertEquals(0, b.byteLength());
    b.setNext(true);
    assertEquals(1, b.length());
    assertEquals(1, b.byteLength());
    b.setNext(true);
    assertEquals(2, b.length());
    assertEquals(1, b.byteLength());
    b.setNextN(6, true);
    assertEquals(8, b.length());
    assertEquals(1, b.byteLength());
    b.setNext(true);
    assertEquals(9, b.length());
    assertEquals(2, b.byteLength());
  }

  @Test
  void testSetNext1() {
    Bits b = new Bits();

    b.setNext(true);
    byte[] exp = {(byte) 0x80};
    assertArrayEquals(exp, b.toByteArray());
    assertEquals("1", b.toBitString());

    b.setNext(true);
    exp[0] = ((byte) 0x80) | 0x40;
    assertArrayEquals(exp, b.toByteArray());
    assertEquals("11", b.toBitString());

    b.setNext(true);
    exp[0] = ((byte) 0x80) | 0x40 | 0x20;
    assertArrayEquals(exp, b.toByteArray());
    assertEquals("111", b.toBitString());

    b.setNext(true);
    exp[0] = ((byte) 0x80) | 0x40 | 0x20 | 0x10;
    assertArrayEquals(exp, b.toByteArray());
    assertEquals("1111", b.toBitString());

    b.setNext(true);
    exp[0] = ((byte) 0x80) | 0x40 | 0x20 | 0x10 | 0x08;
    assertArrayEquals(exp, b.toByteArray());
    assertEquals("1111 1", b.toBitString());

    b.setNext(true);
    exp[0] = ((byte) 0x80) | 0x40 | 0x20 | 0x10 | 0x08 | 0x04;
    assertArrayEquals(exp, b.toByteArray());
    assertEquals("1111 11", b.toBitString());

    b.setNext(true);
    exp[0] = ((byte) 0x80) | 0x40 | 0x20 | 0x10 | 0x08 | 0x04 | 0x02;
    assertArrayEquals(exp, b.toByteArray());
    assertEquals("1111 111", b.toBitString());

    b.setNext(true);
    exp[0] = ((byte) 0x80) | 0x40 | 0x20 | 0x10 | 0x08 | 0x04 | 0x02 | 0x01;
    assertArrayEquals(exp, b.toByteArray());
    assertEquals("1111 1111", b.toBitString());

    b.setNext(true);
    byte[] exp2 = {(byte) 0xFF, (byte) 0x80};
    assertArrayEquals(exp2, b.toByteArray());
    assertEquals("1111 1111 1", b.toBitString());
    assertEquals(9, b.length());
  }

  @Test
  void testSetNextN() {
    Bits b = new Bits();
    b.setNextN(13, true);
    assertEquals(13, b.length());
    assertEquals("1111 1111 1111 1", b.toBitString());
    b.setNextN(3, false);
    assertEquals(16, b.length());
    assertEquals("1111 1111 1111 1000", b.toBitString());

    assertThrows(IllegalArgumentException.class, () -> b.setNextN(0, true));
    assertThrows(IllegalArgumentException.class, () -> b.setNextN(-1, true));
  }

  @Test
  void testSetNextNFrom() {
    Bits b = new Bits();
    b.setNextNFrom(4, 15);
    assertEquals("1111", b.toBitString());
    b.setNextNFrom(8, 255);
    assertEquals("1111 1111 1111", b.toBitString());
    b.setNextNFrom(2, 2);
    assertEquals("1111 1111 1111 10", b.toBitString());
    b.setNextNFrom(3, 2);
    assertEquals("1111 1111 1111 1001 0", b.toBitString());
    String exp = "1111 1111 1111 1001 0";
    for (int i = 0; i < 18; i++) {
      b.setNextNFrom(1, 1);
      exp = exp + "1";
      assertEqualsBits(exp, b);
    }
    b = new Bits();
    b.setNextNFrom(8, 0b10100101);
    assertEquals("1010 0101", b.toBitString());
    b.setNextNFrom(16, 0b1010010110100101);
    assertEquals("1010 0101 1010 0101 1010 0101", b.toBitString());

    // worse case, 10 bits spans 3 dest bytes
    Bits b2 = new Bits();
    b2.setNextN(7, false);
    assertEquals("0000 000", b2.toBitString());
    b2.setNextNFrom(10, 0b1010010101);
    assertEquals("0000 0001 0100 1010 1", b2.toBitString());
    assertThrows(IllegalArgumentException.class, () -> b2.setNextNFrom(0, 1));
    assertThrows(IllegalArgumentException.class, () -> b2.setNextNFrom(64, 1));
  }

  @Test
  void testSetNextNFrom63Bits() {
    Bits b = new Bits();
    b.setNextNFrom(63, Long.MAX_VALUE);
    assertEquals(
        "1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 111",
        b.toBitString());

    Bits b2 = new Bits();
    b2.setNext(1, 1);
    b2.setNextNFrom(63, 0);
    b2.setNext(1);
    assertEquals(
        "1100 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 01",
        b2.toBitString());
  }

  @Test
  void testSetInts() {
    Bits b = new Bits();
    b.setNext(0, 0, 1, 1);
    assertEquals("0011", b.toBitString());
    assertThrows(IllegalArgumentException.class, () -> b.setNext(2));
    assertThrows(IllegalArgumentException.class, () -> b.setNext(-1));
  }

  @Test
  void testSetBooleans() {
    Bits b = new Bits();
    b.setNext(true, false, false, true, false);
    assertEquals("1001 0", b.toBitString());
  }

  @Test
  void testFlip() {
    Bits b = new Bits();
    b.setNext(1, 1, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0);
    assertEquals("1100 1010 0000 1111 0000", b.toBitString());
    b.flip(2, 4);
    assertEquals("1111 1010 0000 1111 0000", b.toBitString());
    b.flip(0, 3);
    assertEquals("0001 1010 0000 1111 0000", b.toBitString());
    b.flip(6, 10);
    assertEquals("0001 1001 1100 1111 0000", b.toBitString());
    b.flip(7, 18);
    assertEquals("0001 1000 0011 0000 1100", b.toBitString());
    b.flip(19, 20);
    assertEquals("0001 1000 0011 0000 1101", b.toBitString());
    b.flip(0, 8);
    assertEquals("1110 0111 0011 0000 1101", b.toBitString());
    b.flip(0, 8);
    assertEquals("0001 1000 0011 0000 1101", b.toBitString());
    b.flip(0, 1);
    assertEquals("1001 1000 0011 0000 1101", b.toBitString());
    b.flip(7, 8);
    assertEquals("1001 1001 0011 0000 1101", b.toBitString());
    b.flip(8, 9);
    assertEquals("1001 1001 1011 0000 1101", b.toBitString());
    b.flip(7, 9);
    assertEquals("1001 1000 0011 0000 1101", b.toBitString());
    assertThrows(IllegalArgumentException.class, () -> b.flip(4, 2));
    assertThrows(IllegalArgumentException.class, () -> b.flip(19, 21));
    assertThrows(IllegalArgumentException.class, () -> b.flip(4, 3));
    assertThrows(IllegalArgumentException.class, () -> b.flip(-1, 3));
  }

  @Test
  void testIsSet() {
    Bits b = new Bits();
    b.setNext(1, 0, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1, 0, 0, 0, 0);
    assertEquals("1010 0101 1111 0000", b.toBitString());
    StringBuilder r = new StringBuilder();
    for (int i = 0; i < 16; i++) {
      r.append(b.isSet(i) ? '1' : '0');
    }
    assertEquals("1010 0101 1111 0000".replace(" ", ""), r.toString());
    assertThrows(IndexOutOfBoundsException.class, () -> b.isSet(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> b.isSet(100));
  }

  @Test
  void testIterator() {
    Bits b = new Bits();
    b.setNext(1, 1, 1, 0, 0, 0);
    Bits.Iterator i = b.iterator();
    assertEquals(0, i.position());
    assertEquals(6, i.remaining());
    assertTrue(i.hasNext());
    assertTrue(i.next());
    assertTrue(i.hasNext());
    assertTrue(i.next());
    assertTrue(i.hasNext());
    assertTrue(i.next());
    assertTrue(i.hasNext());
    assertEquals(3, i.position());
    assertEquals(3, i.remaining());
    assertFalse(i.next());
    assertTrue(i.hasNext());
    assertFalse(i.next());
    assertTrue(i.hasNext());
    assertFalse(i.next());
    assertFalse(i.hasNext());
    assertEquals(6, i.position());
    assertEquals(0, i.remaining());
    assertThrows(NoSuchElementException.class, () -> i.next());
  }

  @Test
  void testIteratorNextN() {
    Bits b = new Bits();
    b.setNext(1, 1, 1, 0, 0, 0, 1);
    assertEquals(1, b.iterator().nextN(1));
    assertEquals(3, b.iterator().nextN(2));
    assertEquals(7, b.iterator().nextN(3));
    assertEquals(14, b.iterator().nextN(4));
    assertEquals(28, b.iterator().nextN(5));
    assertEquals(56, b.iterator().nextN(6));
    assertEquals(113, b.iterator().nextN(7));

    Bits.Iterator i = b.iterator();
    assertEquals(3, i.nextN(2));
    assertEquals(4, i.nextN(3));
    assertEquals(1, i.nextN(2));
    assertFalse(i.hasNext());
    assertThrows(NoSuchElementException.class, () -> i.next());
    assertThrows(NoSuchElementException.class, () -> b.iterator().nextN(10));

    assertThrows(IllegalArgumentException.class, () -> i.nextN(-1));
    assertThrows(IllegalArgumentException.class, () -> i.nextN(0));
    assertThrows(IllegalArgumentException.class, () -> i.nextN(65));
  }

  @Test
  void testIteratorNextN63() {
    Bits b = new Bits();
    b.setNextN(128, true);
    long n = b.iterator().nextN(63);
    assertEquals(0x7FFFFFFFFFFFFFFFL, n);
    n = b.iterator().nextN(64);
    assertEquals(0xFFFFFFFFFFFFFFFFL, n);
  }

  @Test
  void testExpandArray() {
    Bits b = new Bits(8);
    assertEquals(8, b.capacity());
    b.setNextN(10, true);
    byte[] exp = {(byte) 0xFF, (byte) 0x80 | 0x40};
    assertArrayEquals(exp, b.toByteArray());
    assertEquals("1111 1111 11", b.toBitString());
    assertEquals(16, b.capacity());

    exp[1] = (byte) 0xFF;
    b.setNextN(6, true);
    assertArrayEquals(exp, b.toByteArray());
    assertEquals("1111 1111 1111 1111", b.toBitString());
    assertEquals(16, b.capacity());

    b.setNext(true);
    byte[] exp2 = {(byte) 0xFF, (byte) 0xFF, (byte) 0x80};
    assertEquals("1111 1111 1111 1111 1", b.toBitString());
    assertArrayEquals(exp2, b.toByteArray());
    assertEquals(32, b.capacity());

    b = new Bits(8);
    b.setNextN(8, false);
    assertEquals(8, b.capacity());
    b.setNext(false);
    assertEquals(16, b.capacity());
    assertEquals("0000 0000 0", b.toBitString());
  }

  @Test
  void testToArray() {
    Bits b = new Bits();
    b.setNextNFrom(10, 609);
    assertEquals("1001 1000 01", b.toBitString());
    byte[] exp = {(byte) 0x98, (byte) 0x40};
    assertArrayEquals(exp, b.toByteArray());
    // the returned value should be a copy, mutating it shouldn't mutate the bits
    b.toByteArray()[0] = (byte) 0xFF;
    assertEquals("1001 1000 01", b.toBitString());
  }

  @Test
  void testWriteToBytes() {
    Bits b = new Bits();
    b.setNextNFrom(10, 609);
    byte[] dest = new byte[2];
    b.writeTo(dest, 0);
    byte[] exp = {(byte) 0x98, (byte) 0x40};
    assertArrayEquals(exp, dest);
    assertArrayEquals(b.toByteArray(), dest);
  }

  @Test
  void testWriteToBytesOffset() {
    Bits b = new Bits();
    b.setNextNFrom(10, 609);
    byte[] dest = new byte[10];
    b.writeTo(dest, 2);
    byte[] exp = {0, 0, (byte) 0x98, (byte) 0x40, 0, 0, 0, 0, 0, 0};
    assertArrayEquals(exp, dest);

    dest = new byte[10];
    b.writeTo(dest, 8);
    byte[] exp2 = {0, 0, 0, 0, 0, 0, 0, 0, (byte) 0x98, (byte) 0x40};
    assertArrayEquals(exp2, dest);

    assertThrows(IllegalArgumentException.class, () -> b.writeTo(new byte[10], 9));
  }

  @Test
  void testWriteToStream() throws IOException {
    Bits b = new Bits();
    b.setNextNFrom(10, 609);
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    b.writeTo(os);
    byte[] exp = {(byte) 0x98, (byte) 0x40};
    assertArrayEquals(exp, os.toByteArray());
  }

  // helper for generating readable failure messages if the bit patterns don't match
  static void assertEqualsBits(String exp, Bits act) {
    if (exp.replace(" ", "").equals(act.toBitString().replace(" ", ""))) {
      return;
    }
    // build a version of act with the same spacing as exp
    StringBuilder r = new StringBuilder(exp.length());
    int bitIdx = 0;
    for (int p = 0; p < exp.length(); p++) {
      char c = exp.charAt(p);
      if ((c == '0' || c == '1')) {
        r.append(act.isSet(bitIdx) ? '1' : '0');
        if (++bitIdx >= act.length()) {
          break;
        }
      } else {
        r.append(c);
      }
    }
    for (; bitIdx < act.length(); bitIdx++) {
      r.append(act.isSet(bitIdx) ? '1' : '0');
    }
    assertEquals(exp, r.toString(), "generated Bits do not match expected bit pattern");
  }
}

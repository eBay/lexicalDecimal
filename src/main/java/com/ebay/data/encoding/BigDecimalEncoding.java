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

import java.io.EOFException;
import java.math.BigDecimal;
import java.math.BigInteger;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * BigDecimalEncoding encodes and decodes BigDecimal numbers into a lexicographical encoding.
 *
 * <p>This is using the encoding described in the Decimal Infinite paper.
 *
 * <p>Fourny, Ghislain. (2015). decimalInfinite: All Decimals In Bits, No Loss, Same Order, Simple.
 * https://www.researchgate.net/publication/278734280_decimalInfinite_All_Decimals_In_Bits_No_Loss_Same_Order_Simple
 *
 * <p>In summary it takes a number converts it to a y.zzzz x 10^exp equivalent then encodes the
 * overall sign, exponent sign, exponent and significand in that order. While the encoding described
 * in the paper supports arbitrary numbers, this implementation is limited to what can be
 * represented by BigDecimal.
 */
@ParametersAreNonnullByDefault
public class BigDecimalEncoding {

  private BigDecimalEncoding() {
    // Don't construct me
  }

  private static final BigInteger ONE_THOUSAND = BigInteger.valueOf(1000);

  /**
   * Encodes the supplied BigDecimal into a lexicographical encoding.
   *
   * @param d the BigDecimal value to encode, can't be null.
   * @return the encoded value.
   */
  public static Bits encode(final BigDecimal d) {
    final int sign = d.compareTo(BigDecimal.ZERO);
    if (sign == 0) {
      Bits b = new Bits(8);
      b.setNext(true, false);
      return b;
    }
    // We have to normalize the value to be [1-10].ddddd x10^exponent.
    // If the value is negative then subtract the value from 10.
    BigDecimal val = d.abs();
    int exp = 0;
    while (val.compareTo(BigDecimal.TEN) >= 0) {
      val = val.movePointLeft(1);
      exp++;
    }
    while (val.compareTo(BigDecimal.ONE) < 0) {
      val = val.movePointRight(1);
      exp--;
    }
    if (sign < 0) {
      val = BigDecimal.TEN.subtract(val);
    }
    // break the value into separate leading digit and fractional value
    BigInteger significand = val.toBigInteger();
    BigDecimal significandFraction = val.subtract(new BigDecimal(significand));

    // Now we have a normalized value of sign, significand + significandFraction * 10^exp

    Bits bits = new Bits();
    // split exp into sign & abs value
    boolean expNotNegative = exp >= 0;
    exp = exp < 0 ? -exp : exp;
    // encoding of the overall sign takes the first 2 bits, remember T for later on.
    int t;
    if (sign > 0) {
      bits.setNext(true, false);
      t = expNotNegative ? 1 : 0;
    } else {
      bits.setNext(false, false);
      t = expNotNegative ? 0 : 1;
    }

    // modified gamma encoding of the exponent.
    // a) The exponent is offset by +2, for example, 4 is encoded with the modified gamma code of 6
    long offsetExp = exp + 2;

    // b) Call N the number of its digits in its binary representation, e.g. 6 is 110, so N is 3
    // c) The first digit is replaced with N-1 ones, followed by a zero (e.g. 110 -> 110 10)
    int bitLength = 64 - Long.numberOfLeadingZeros(offsetExp);
    bits.setNextN(bitLength - 1, true);
    bits.setNext(false);

    // All the relevant bits of offsetExp minus its first bit (which was replaced by the prefix
    // encoding above)
    bits.setNextNFrom(bitLength - 1, offsetExp);

    // Once the absolute value of the exponent has been encoded, it is either negated if T=0, or
    // left unchanged if T=1.
    if (t == 0) {
      bits.flip(2, bits.length());
    }

    // Encoding of the significand.
    //
    // Its initial digit (before the decimal point) is encoded on 4 bits (tetrade) in its natural
    // binary representation.
    bits.setNextNFrom(4, significand.intValueExact());

    // The remaining digits (after the decimal point) are organized in groups of 3 (declets). Each
    // declet is encoded in its natural binary representation on 10 bits. Trailing 0s are added to
    // make sure that the last group also has 3 digits
    if (significandFraction.compareTo(BigDecimal.ZERO) != 0) {
      String f = significandFraction.toPlainString();
      f = f.substring(f.indexOf('.') + 1);
      while (f.length() > 0) {
        if (f.length() == 1) {
          f = f + "00";
        } else if (f.length() == 2) {
          f = f + "0";
        }
        String declet = f.substring(0, 3);
        f = f.substring(3);
        int groupVal = Integer.parseUnsignedInt(declet);
        bits.setNextNFrom(10, groupVal);
      }
    }
    return bits;
  }

  /**
   * Decodes a value that was generated with encode back to a BigDecimal.
   *
   * @param encoded the encoded bytes previously generated by encode, can't be null.
   * @return the resulting decoded BigDecimal value
   * @throws EOFException If the end of the input is unexpectedly reached.
   * @throws InvalidEncodingException if there is a problem decoding the encoded data.
   */
  public static BigDecimal decode(byte[] encoded) throws EOFException, InvalidEncodingException {
    if (encoded.length == 0) {
      throw new EOFException("Cannot decode empty DecimalInfinite, should contain at least 1 byte");
    }
    if (encoded.length == 1 && encoded[0] == (byte) 0x80) {
      return BigDecimal.ZERO;
    }
    // decode overall sign & exponent sign
    final Bits bits = new Bits(encoded);
    final Bits.Iterator it = bits.iterator();
    final boolean sign = it.next(); // bit 0
    if (it.next()) {
      // bit 1 should always be zero
      throw new InvalidEncodingException("Cannot decode DecimalInfinite, invalid sign bits");
    }
    final boolean bitT = it.next(); // bit 2
    final boolean expSign = sign ? bitT : !bitT;
    // decode exponent: first off, decode the prefix that indicates the length of the encoded
    // exponent
    int n = 1;
    while (it.next() == bitT) {
      n++;
    }
    // number of bits the exponent is encoded on, including the coded prefix.
    int expBits = n * 2 + 1;
    if (!bitT) {
      bits.flip(2, 2 + expBits);
    }
    // now decode the actual exponent value, remembering that the prefix provides
    // the MSB
    long exp = (1 << n) | it.nextN(n);
    // remove the offset of 2, and set the sign appropriately
    exp -= 2;
    // does this overflow BigDecimal?
    if (exp > Integer.MAX_VALUE) {
      throw new InvalidEncodingException(
          "Cannot decode DecimalInfinite, unsupported exponent value of " + exp);
    }
    if (!expSign) {
      exp = -exp;
    }
    // Now decode the significand: The first digit always takes 4 bits.
    long significand = it.nextN(4);
    if (significand > 9) {
      throw new InvalidEncodingException(
          "Cannot decode DecimalInfinite, invalid significand of " + significand);
    }
    // Now decode the decimal part, which was encoded in groups of 10 bits.
    BigInteger f = BigInteger.ZERO;
    int decimalDigits = 0;
    while (it.remaining() >= 10) {
      long g = it.nextN(10);
      if (g > 999) {
        throw new InvalidEncodingException(
            "Cannot decode DecimalInfinite, invalid significand decimal group of " + g);
      }
      f = f.multiply(ONE_THOUSAND).add(BigInteger.valueOf(g));
      decimalDigits += 3;
    }
    BigDecimal result = new BigDecimal(BigInteger.valueOf(significand));
    result = result.add(new BigDecimal(f, decimalDigits));
    if (!sign) {
      result = result.subtract(BigDecimal.TEN);
    }
    result = result.scaleByPowerOfTen((int) exp);
    return result;
  }
}

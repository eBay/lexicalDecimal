package com.ebay.data.encoding;

import static com.ebay.data.encoding.BigDecimalEncoding.decode;
import static com.ebay.data.encoding.BigDecimalEncoding.encode;
import static com.ebay.data.encoding.BitsTest.assertEqualsBits;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.primitives.UnsignedBytes;
import java.io.EOFException;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InfiniteDecimalTest {

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("encodeTestSource")
  void testEncodeDecode(String testName, String input, String expBits)
      throws EOFException, InvalidEncodingException {
    // test encoding against expected bit pattern, and then round trip though encode/decode
    // and check we get the same value back.
    BigDecimal in = new BigDecimal(input);
    Bits b = encode(in);
    assertEqualsBits(expBits, b);
    BigDecimal decoded = decode(b.toByteArray());
    if (in.compareTo(decoded) != 0) {
      assertEquals(in.toPlainString(), decoded.toPlainString());
    }
  }

  private static class InputAndEncoded {
    final BigDecimal input;
    final byte[] encoded;

    InputAndEncoded(String input) {
      this.input = new BigDecimal(input);
      this.encoded = encode(this.input).toByteArray();
    }
  }

  @Test
  void testEncodedOrderingOfEncodingTestValues() {
    List<InputAndEncoded> values =
        encodeTestSource()
            .map(args -> new InputAndEncoded((String) args.get()[1]))
            .collect(Collectors.toList());
    // Sort them by the encoded value. Then check that the input BigDecimal values
    // are in the correct order.
    values.sort((x, y) -> UnsignedBytes.lexicographicalComparator().compare(x.encoded, y.encoded));
    BigDecimal prev = null;
    for (InputAndEncoded v : values) {
      if (prev != null) {
        assertTrue(v.input.compareTo(prev) > 0);
      }
      prev = v.input;
    }
  }

  static Stream<Arguments> encodeTestSource() {
    return Stream.of(
        Arguments.of("Paper_Sample", "-103.2", "00 001 11 1000 1111001000"),
        Arguments.of("Paper_Sample", "-0.0405", "00 110 00 0101 1110110110"),
        // This one is from the paper, but i don't think the paper is correct for this.
        // If you look at the 2 declet groups (071, 060), the encoded has a higher value
        // for 060 than 071, which can't be right.
        // Arguments.of("Paper_Sample", "0.707106", "10 01 0 0111 0001000111 0001111000"),
        Arguments.of(
            "Paper_Sample", "4005012345", "10 1110 011 0100 0000000101 0000001100 0101011001"),
        Arguments.of("Paper_Sample", "-15", "00 010 1000 0111110100"),
        Arguments.of("Paper_Sample", "-14", "00 010 1000 1001011000"),
        Arguments.of("Paper_Sample", "-13", "00 010 1000 1010111100"),
        Arguments.of("Paper_Sample", "-12", "00 010 1000 1100100000"),
        Arguments.of("Paper_Sample", "-11", "00 010 1000 1110000100"),
        Arguments.of("Paper_Sample", "-10", "00 010 1001"),
        Arguments.of(
            "Paper_Sample",
            "-9",
            "00 011 0001"), // paper says 00 011 0010, but that's the same as -8
        Arguments.of("Paper_Sample", "-8", "00 011 0010"),
        Arguments.of("Paper_Sample", "-7", "00 011 0011"),
        Arguments.of("Paper_Sample", "0", "10"),
        Arguments.of("Paper_Sample", "1", "10 100 0001"),
        Arguments.of("Paper_Sample", "2", "10 100 0010"),
        Arguments.of("Paper_Sample", "3", "10 100 0011"),
        Arguments.of("Paper_Sample", "4", "10 100 0100"),
        Arguments.of("Paper_Sample", "5", "10 100 0101"),
        Arguments.of("Paper_Sample", "6", "10 100 0110"),
        Arguments.of("Paper_Sample", "7", "10 100 0111"),
        Arguments.of("Paper_Sample", "8", "10 100 1000"),
        Arguments.of("Paper_Sample", "9", "10 100 1001"),
        Arguments.of("Paper_Sample", "10", "10 101 0001"),
        Arguments.of("Paper_Sample", "11", "10 101 0001 0001100100"),
        Arguments.of("Paper_Sample", "12", "10 101 0001 0011001000"),
        Arguments.of("Paper_Sample", "13", "10 101 0001 0100101100"),
        Arguments.of("Paper_Sample", "14", "10 101 0001 0110010000"),
        Arguments.of("Paper_Sample", "15", "10 101 0001 0111110100"),
        // These 2 are from the samples in the JSONiq implementation
        // But these appear to be wrong as well, e.g. 100 should be 10 110 00 0001 (1.0 x 10^2)
        //  Arguments.of("iqSample", "100", "10 110 00 0100"),
        // Arguments.of(
        // "iqSample", "3.1415926535", "10 100 0110 1000110101 0010100000 1010001101 0111110100"),
        Arguments.of("Hand Cranked", "0.140", "10 010 0001 0110010000"), // 1.4 x 10^-1
        Arguments.of("Hand Cranked", "-0.041", "00 110 00 0101 1110000100")); // -4.1 x 10^-2
  }

  @ParameterizedTest(name = "{0} {1} - {2}")
  @MethodSource("testOrderingSource")
  void testOrdering(
      String name, BigDecimal start, BigDecimal end, Function<BigDecimal, BigDecimal> step)
      throws EOFException, InvalidEncodingException {
    assertTrue(start.compareTo(end) >= 0, "start should be larger than end");
    Bits prev = null;
    while (start.compareTo(end) >= 0) {
      Bits enc = encode(start);
      if (prev != null) {
        Bits p = prev; // keep lambda happy
        assertTrue(
            UnsignedBytes.lexicographicalComparator().compare(prev.toByteArray(), enc.toByteArray())
                > 0,
            () ->
                String.format(
                    "%s expected to be larger than %s", enc.toBitString(), p.toBitString()));
      }
      BigDecimal decoded = decode(enc.toByteArray());
      if (start.compareTo(decoded) != 0) {
        assertEquals(start.toPlainString(), decoded.toPlainString());
      }
      prev = enc;
      start = step.apply(start);
    }
  }

  static Stream<Arguments> testOrderingSource() {
    BigDecimal smallStep = new BigDecimal("0.001");
    BigDecimal smallStep3 = new BigDecimal("0.0003");
    Function<BigDecimal, BigDecimal> fnSmallStep = v -> v.subtract(smallStep);
    Function<BigDecimal, BigDecimal> fnSmallStep3 = v -> v.subtract(smallStep3);
    Function<BigDecimal, BigDecimal> fnExpStep = v -> v.divide(BigDecimal.TEN);
    Function<BigDecimal, BigDecimal> fnExpStepUp = v -> v.multiply(BigDecimal.TEN);
    return Stream.of(
        Arguments.of("Around Zero", new BigDecimal("1.11"), new BigDecimal("-1.01"), fnSmallStep3),
        Arguments.of(
            "Expenonents",
            new BigDecimal("10000000000000000000000"),
            new BigDecimal("0.000000000000000001"),
            fnExpStep),
        Arguments.of(
            "-ve Expenonents",
            new BigDecimal("-0.000000000000000001"),
            new BigDecimal("-10000000000000000000000"),
            fnExpStepUp),
        Arguments.of(
            "Around large -ve number",
            new BigDecimal("-121313414914314143.2"),
            new BigDecimal("-121313414914314144.8"),
            fnSmallStep3),
        Arguments.of(
            "Around large ve number",
            new BigDecimal("123123912313121313414914314149.01"),
            new BigDecimal("123123912313121313414914314148.8"),
            fnSmallStep));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("decodeErrorsSource")
  void testDecodeErrors(
      Class<? extends Exception> expException, String exceptionContainsText, Bits src) {
    Exception e = assertThrows(expException, () -> decode(src.toByteArray()));
    assertTrue(e.getMessage().contains(exceptionContainsText));
  }

  static Stream<Arguments> decodeErrorsSource() {
    return Stream.of(
        Arguments.of(EOFException.class, "should contain at least 1 byte", new Bits()),
        Arguments.of(
            InvalidEncodingException.class, "invalid sign bits", new Bits().setNextN(4, true)),
        Arguments.of(
            InvalidEncodingException.class,
            "unsupported exponent value",
            new Bits().setNext(true, false).setNextN(32, true).setNext(false).setNextN(32, true)),
        Arguments.of(
            InvalidEncodingException.class,
            "invalid significand of",
            new Bits().setNext(1, 0, 1, 0, 0, 1, 1, 1, 1)),
        Arguments.of(
            InvalidEncodingException.class,
            "invalid significand decimal group",
            new Bits().setNext(1, 0, 1, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)));
  }
}

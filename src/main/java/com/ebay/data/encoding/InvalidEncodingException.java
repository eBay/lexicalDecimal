package com.ebay.data.encoding;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * InvalidEncodingException indicates that the provided encoded bytes is not a valid encoding of a
 * value.
 */
@ParametersAreNonnullByDefault
public class InvalidEncodingException extends Exception {

  private static final long serialVersionUID = 1L;

  InvalidEncodingException(String msg) {
    super(msg);
  }
}

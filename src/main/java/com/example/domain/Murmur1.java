package com.example.domain;

public class Murmur1 {

  public static long hash(String input) {
    if (input == null || input.isEmpty()) {
      return 0L;
    }

    byte[] data = input.getBytes();
    int length = data.length;
    long h1 = 0x9747b28c; // seed

    // Body
    for (int i = 0; i < length; i += 4) {
      long k1 = 0;

      if (i < length)
        k1 |= (data[i] & 0xff);
      if (i + 1 < length)
        k1 |= ((data[i + 1] & 0xff) << 8);
      if (i + 2 < length)
        k1 |= ((data[i + 2] & 0xff) << 16);
      if (i + 3 < length)
        k1 |= ((data[i + 3] & 0xff) << 24);

      k1 *= 0x5bd1e995;
      k1 ^= k1 >>> 24;
      k1 *= 0x5bd1e995;

      h1 ^= k1;
      h1 *= 0x5bd1e995;
    }

    // Tail
    long k1 = 0;
    int tailStart = (length >> 2) << 2;
    switch (length & 3) {
      case 3:
        k1 ^= (data[tailStart + 2] & 0xff) << 16;
      case 2:
        k1 ^= (data[tailStart + 1] & 0xff) << 8;
      case 1:
        k1 ^= (data[tailStart] & 0xff);
        k1 *= 0x5bd1e995;
        k1 ^= k1 >>> 24;
        k1 *= 0x5bd1e995;
        h1 ^= k1;
    }

    // Finalization
    h1 ^= length;
    h1 ^= h1 >>> 13;
    h1 *= 0x5bd1e995;
    h1 ^= h1 >>> 15;

    return h1;
  }
}

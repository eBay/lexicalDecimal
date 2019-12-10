# Decimal Infinite

## Description

This library provides an implementation of a lexicographical encoding/decoding of BigDecimals. This allows you to store
BigDecimal values as byte arrays in a key value store, or other similar storage engines. The ordering of the encoded
value matches the ordering of the BigDecimal values.

This uses the encoding described in the Decimal Infinite paper.
 
Fourny, Ghislain. (2015). [decimalInfinite: All Decimals In Bits, No Loss, Same Order, Simple.](https://www.researchgate.net/publication/278734280_decimalInfinite_All_Decimals_In_Bits_No_Loss_Same_Order_Simple)
  
## Usage

1. Add a maven dependency.

```xml
 <dependency>
   <groupId>com.ebay.data.encoding</groupId>
   <artifactId>decimal-infinite</artifactId>
   <version>1.0</version>
 </dependency> 
 ```
2. Encode a value

```java
	BigDecimal val = new BigDecimal("1234.5678");
	byte []encoded = BigDecimalEncoding.encode(val).toByteArray();
```

3. Decode a previously encoded value

```java
	byte []encoded ...
	BigDecimal val = BigDecimalEncoding.decode(encoded);
```

4. The Bits class

Encoding returns an instance of Bits that represents the encoded value. As above it can generate
a byte array. In addition it can also write the bits to an existing array or Stream.

```java
	OutputStream os = getOutputStream();
	BigDecimal val = new BigDecimal("1234.5678");
	BigDecimalEncoding.encode(val).writeTo(os);
```

## See Also

* [decimalgamma-cpp](https://github.com/ghislainfourny/decimalgamma-cpp) Is a c++ implementation from the paper's Author.
* [decimalInfinite](https://github.com/ghislainfourny/decimalInfinite) a JSONiq implementation.


## License

package rx.observables;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.MalformedInputException;

import org.junit.Test;

import rx.Observable;
import rx.observables.BlockingObservable;
import rx.observables.StringObservable;
import rx.util.AssertObservable;

public class StringObservableTest {

    @Test
    public void testMultibyteSpanningTwoBuffers() {
        Observable<byte[]> src = Observable.from(new byte[] { (byte) 0xc2 }, new byte[] { (byte) 0xa1 });
        String out = StringObservable.decode(src, "UTF-8").toBlockingObservable().single();

        assertEquals("\u00A1", out);
    }

    @Test
    public void testMalformedAtTheEndReplace() {
        Observable<byte[]> src = Observable.from(new byte[] { (byte) 0xc2 });
        String out = StringObservable.decode(src, "UTF-8").toBlockingObservable().single();

        // REPLACEMENT CHARACTER
        assertEquals("\uFFFD", out);
    }

    @Test
    public void testMalformedInTheMiddleReplace() {
        Observable<byte[]> src = Observable.from(new byte[] { (byte) 0xc2, 65 });
        String out = StringObservable.decode(src, "UTF-8").toBlockingObservable().single();

        // REPLACEMENT CHARACTER
        assertEquals("\uFFFDA", out);
    }

    @Test(expected = RuntimeException.class)
    public void testMalformedAtTheEndReport() {
        Observable<byte[]> src = Observable.from(new byte[] { (byte) 0xc2 });
        CharsetDecoder charsetDecoder = Charset.forName("UTF-8").newDecoder();
        StringObservable.decode(src, charsetDecoder).toBlockingObservable().single();
    }

    @Test(expected = RuntimeException.class)
    public void testMalformedInTheMiddleReport() {
        Observable<byte[]> src = Observable.from(new byte[] { (byte) 0xc2, 65 });
        CharsetDecoder charsetDecoder = Charset.forName("UTF-8").newDecoder();
        StringObservable.decode(src, charsetDecoder).toBlockingObservable().single();
    }

    @Test
    public void testPropogateError() {
        Observable<byte[]> src = Observable.from(new byte[] { 65 });
        Observable<byte[]> err = Observable.error(new IOException());
        CharsetDecoder charsetDecoder = Charset.forName("UTF-8").newDecoder();
        try {
            StringObservable.decode(Observable.concat(src, err), charsetDecoder).toList().toBlockingObservable().single();
            fail();
        } catch (RuntimeException e) {
            assertEquals(IOException.class, e.getCause().getClass());
        }
    }

    @Test
    public void testPropogateErrorInTheMiddleOfMultibyte() {
        Observable<byte[]> src = Observable.from(new byte[] { (byte) 0xc2 });
        Observable<byte[]> err = Observable.error(new IOException());
        CharsetDecoder charsetDecoder = Charset.forName("UTF-8").newDecoder();
        try {
            StringObservable.decode(Observable.concat(src, err), charsetDecoder).toList().toBlockingObservable().single();
            fail();
        } catch (RuntimeException e) {
            assertEquals(MalformedInputException.class, e.getCause().getClass());
        }
    }

    @Test
    public void testEncode() {
        assertArrayEquals(
                new byte[] { (byte) 0xc2, (byte) 0xa1 },
                StringObservable.encode(Observable.just("\u00A1"), "UTF-8").toBlockingObservable().single());
    }

    @Test
    public void testSplitOnCollon() {
        testSplit("boo:and:foo", ":", 0, "boo", "and", "foo");
    }
    @Test
    public void testSplitOnOh() {
        testSplit("boo:and:foo", "o", 0, "b", "", ":and:f");
    }

    public void testSplit(String str, String regex, int limit, String... parts) {
        testSplit(str, regex, 0, Observable.from(str), parts);
        for (int i = 0; i < str.length(); i++) {
            String a = str.substring(0, i);
            String b = str.substring(i, str.length());
            testSplit(a+"|"+b, regex, limit, Observable.from(a, b), parts);
        }
    }

    public void testSplit(String message, String regex, int limit, Observable<String> src, String... parts) {
        Observable<String> act = StringObservable.split(src, regex);
        Observable<String> exp = Observable.from(parts);
        AssertObservable.assertObservableEqualsBlocking("when input is "+message+" and limit = "+ limit, exp, act);
    }
}

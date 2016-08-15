import com.plotsquared.iserver.matching.ViewPattern;
import org.junit.Assert;
import org.junit.Test;

public class ViewMatcherTest
{

    @Test
    public void testMatches()
    {
        final ViewPattern pattern1 = new ViewPattern( "<file>[extension]" );
        final ViewPattern pattern2 = new ViewPattern( "public/<required>/[optional]" );

        final String in1 = "test.html";
        final String in2 = "/public/foo/";
        final String in3 = "/public/foo/bar";
        final String in4 = "/public/foo/bar.html";
        final String in5 = "test";
        final String in6 = "";

        Assert.assertNotNull( pattern1 + ": " + in1, pattern1.matches( in1 ) );
        Assert.assertNotNull( pattern2 + ": " + in2, pattern2.matches( in2 ) );
        Assert.assertNotNull( pattern2 + ": " + in3, pattern2.matches( in3 ) );
        Assert.assertNotNull( pattern1 + ": " + in5, pattern1.matches( in5 ) );

        Assert.assertNull( pattern2 + ": " + in4, pattern2.matches( in4 ) );
        // Assert.assertNull( pattern1 + ": " + in6, pattern1.matches( in6 ) );
    }

}

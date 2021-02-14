package org.andstatus.app.net.social;

import org.andstatus.app.tests.R;
import org.andstatus.app.util.RawResourceUtils;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class AJsonCollectionTest {

    @Test
    public void testDefaultPage() throws IOException {
        AJsonCollection c1 = AJsonCollection.of(RawResourceUtils.getString(R.raw.activitypub_inbox_pleroma_default));
        assertEquals(c1.toString(), "https://pleroma.site/users/AndStatus/inbox", c1.getId());
        assertEquals(c1.toString(), Optional.of("https://pleroma.site/users/AndStatus/inbox?page=true"), c1.firstPage.id);

        AJsonCollection c2 = AJsonCollection.of(RawResourceUtils.getString(R.raw.activitypub_inbox_pleroma_first));
        assertEquals(c2.toString(), "https://pleroma.site/users/AndStatus/inbox?max_id=9jPPRXLA2WW7NDFvn6&page=true", c2.getId());
        assertEquals(c2.toString(), Optional.empty(), c2.firstPage.id);
    }

}

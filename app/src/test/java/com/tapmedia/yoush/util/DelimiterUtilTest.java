package com.tapmedia.yoush.util;


import android.text.TextUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static junit.framework.Assert.assertEquals;

@RunWith(PowerMockRunner.class)
@PrepareForTest(TextUtils.class)
public class DelimiterUtilTest {

  @Before
  public void setup() {
    PowerMockito.mockStatic(TextUtils.class);
    PowerMockito.when(TextUtils.isEmpty(Mockito.anyString())).thenAnswer((Answer<Boolean>) invocation -> {
      if (invocation.getArguments()[0] == null) return true;
      return ((String) invocation.getArguments()[0]).isEmpty();
    });
  }

  @Test
  public void testEscape() {
    assertEquals(DelimiterUtil.escape("MTV Music", ' '), "MTV\\ Music");
    assertEquals(DelimiterUtil.escape("MTV  Music", ' '), "MTV\\ \\ Music");

    assertEquals(DelimiterUtil.escape("MTV,Music", ','), "MTV\\,Music");
    assertEquals(DelimiterUtil.escape("MTV,,Music", ','), "MTV\\,\\,Music");

    assertEquals(DelimiterUtil.escape("MTV Music", '+'), "MTV Music");
  }

  @Test
  public void testSplit() {
    String[] parts = DelimiterUtil.split("MTV\\ Music", ' ');
    assertEquals(parts.length, 1);
    assertEquals(parts[0], "MTV\\ Music");

    parts = DelimiterUtil.split("MTV Music", ' ');
    assertEquals(parts.length, 2);
    assertEquals(parts[0], "MTV");
    assertEquals(parts[1], "Music");
  }

  @Test
  public void testEscapeSplit() {
    String   input        = "MTV Music";
    String   intermediate = DelimiterUtil.escape(input, ' ');
    String[] parts        = DelimiterUtil.split(intermediate, ' ');

    assertEquals(parts.length, 1);
    assertEquals(parts[0], "MTV\\ Music");
    assertEquals(DelimiterUtil.unescape(parts[0], ' '), "MTV Music");

    input        = "MTV\\ Music";
    intermediate = DelimiterUtil.escape(input, ' ');
    parts        = DelimiterUtil.split(intermediate, ' ');

    assertEquals(parts.length, 1);
    assertEquals(parts[0], "MTV\\\\ Music");
    assertEquals(DelimiterUtil.unescape(parts[0], ' '), "MTV\\ Music");
  }

}

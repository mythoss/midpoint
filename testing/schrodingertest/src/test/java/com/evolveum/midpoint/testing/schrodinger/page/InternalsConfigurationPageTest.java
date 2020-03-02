/**
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.testing.schrodinger.page;

import com.evolveum.midpoint.schrodinger.component.configuration.ClockTab;
import com.evolveum.midpoint.schrodinger.page.configuration.InternalsConfigurationPage;
import com.evolveum.midpoint.testing.schrodinger.TestBase;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Hiroyuki Wada
 */
public class InternalsConfigurationPageTest extends TestBase {

    private InternalsConfigurationPage configPage;

    @BeforeMethod
    private void openPage() {
        configPage = basicPage.internalsConfiguration();
    }

    @AfterClass
    private void cleanup() {
        // Reset time because TestBase#afterClass() doesn't reset the clock.
        configPage = basicPage.internalsConfiguration();
        configPage.clockTab().resetTime();
    }

    @Test
    public void changeTime() {
        ClockTab clockTab = configPage.clockTab();

        clockTab.changeTime("5/15/2099", "10", "30", ClockTab.AmOrPmChoice.PM);

        Assert.assertTrue(basicPage.feedback().isSuccess());

        basicPage.aboutPage();
        clockTab = basicPage.internalsConfiguration().clockTab();

        Assert.assertEquals(clockTab.date(), "5/15/2099");
        Assert.assertEquals(clockTab.hours(), "10");
        Assert.assertEquals(clockTab.minutes(), "30");
        Assert.assertEquals(clockTab.amOrPmChoice(), ClockTab.AmOrPmChoice.PM.name());
    }

    @Test
    public void resetTime() {
        ClockTab clockTab = configPage.clockTab();

        clockTab.resetTime();

        Assert.assertTrue(basicPage.feedback().isSuccess());
    }
}
package me.earthme.luminol;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectPackages("me.earthme.luminol")
@ConfigurationParameter(key = "TestSuite", value = "Normal")
public class CustomApiTestSuite {
}

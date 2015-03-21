<?xml version='1.0' encoding='utf-8' ?>
<!-- 
    Copyright (C) 2012-2014 yvolk (Yuri Volkov), http://yurivolkov.com
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
         http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    This XSL stylesheet is used to transform changes.xml into HTML format    
 -->
<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:android="http://schemas.android.com/apk/res/android"
  version="1.0">
<xsl:output method="html" encoding='UTF-8' media-type="text/html; charset=UTF-8"/>

<xsl:template match="/">
<html>
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
    <base href="../../assets/" target="_self" />    
    <link rel="stylesheet" href="styles.css" type="text/css" />
    <title>
        <xsl:copy-of select="/document/header/title/node()"/>
    </title>
  </head>
  <body>
  	<h1><xsl:copy-of select="/document/header/title/node()"/></h1>
	<xsl:apply-templates select="/document/header/subtitle" />
  	<xsl:for-each select="/document/release">
      <h2><xsl:value-of select="@versionDate"/> 
          v.<xsl:value-of select="@android:versionName"/> (<xsl:value-of select="@android:versionCode"/>)
          <xsl:value-of select="@versionTitle"/></h2>
      <ol>
      <xsl:for-each select="changes/change">  
        <li><xsl:copy-of select="node()"/></li>
      </xsl:for-each>   
      </ol>  
    </xsl:for-each>   
  </body>
</html>
</xsl:template>

<xsl:template match="subtitle">
    <p><xsl:copy-of select="node()"/></p>
</xsl:template>

</xsl:stylesheet>

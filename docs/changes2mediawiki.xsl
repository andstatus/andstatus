<?xml version='1.0' encoding='utf-8' ?>
<!-- 
    Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
         http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    This XSL stylesheet is used to transform changes.xml into Mediawiki format    
 -->
<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:android="http://schemas.android.com/apk/res/android"
  version="1.0">
<xsl:output method="text" encoding='UTF-8' media-type="text/plain; charset=UTF-8"/>

<xsl:template match="/">
	<xsl:apply-templates select="/document/header/title" />
	<xsl:apply-templates select="/document/header/subtitle" />
	<xsl:for-each select="/document/release">
		<xsl:call-template name="release" />
	</xsl:for-each>   
</xsl:template>

<xsl:template match="title">
	<xsl:message>Template 'title'</xsl:message>
	<xsl:text>= </xsl:text>
	<xsl:call-template name="oneLine" />
	<xsl:text> =&#xa;</xsl:text>
</xsl:template>

<xsl:template match="subtitle">
	<xsl:message>Template 'subtitle'</xsl:message>
	<xsl:call-template name="oneLine" />
	<xsl:text>&#xa;&#xa;</xsl:text>
</xsl:template>

<xsl:template name="release">
	<xsl:text>== </xsl:text>
	<xsl:value-of select="@versionDate"/> 
	<xsl:text> v.</xsl:text>
	<xsl:value-of select="@android:versionName"/>
	<xsl:text> (</xsl:text>
	<xsl:value-of select="@android:versionCode"/>
	<xsl:text>) </xsl:text>
	<xsl:value-of select="@versionTitle"/>
	<xsl:text> ==&#xa;</xsl:text>
	<xsl:apply-templates select="changes/*" />
	<xsl:text>&#xa;</xsl:text>
</xsl:template>

<xsl:template match="change">
	<xsl:message>Template 'change'</xsl:message>
	<xsl:text># </xsl:text>
	<xsl:call-template name="oneLine" />
  	<xsl:text>&#xa;</xsl:text>
</xsl:template>

<xsl:template name="oneLine">
	<xsl:message>Template 'oneLine'</xsl:message>
	<xsl:for-each select="node()">
		<xsl:message>element '<xsl:value-of select="name()" />'</xsl:message>
		<xsl:choose>
			<xsl:when test="name() = 'a'">
				<xsl:text> [</xsl:text>
				<xsl:value-of select="./@href" />
				<xsl:text> </xsl:text>
				<xsl:value-of select="normalize-space(.)" />
				<xsl:text>] </xsl:text>
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="normalize-space(.)" />
			</xsl:otherwise>
		</xsl:choose>
	</xsl:for-each>
</xsl:template>

</xsl:stylesheet>

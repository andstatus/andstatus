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
    <title>
        <xsl:copy-of select="/document/header/title/node()"/>
    </title>
    <style type="text/css">
        body {
        margin-top:0;
        margin-bottom: 2px;
        margin-left: 2px;
        margin-right: 2px;
        font-size: 80%;
        font-family:verdana,arial,helvetica,sans-serif;
        font-weight:normal;
        text-align:left;
        text-decoration:none;
        background-color: #FFFFFF;
        color:black;
        }

        a:link {
        color: #0000FF;
        }

        a:active {
        color: #FF33CC;
        }

        a:visited {
        color: #800080;
        }
        a.bookmark
        {
        display: none;
        }

        B, STRONG {
        font-family:arial,verdana,helvetica,sans-serif;
        }

        H1{
        font-size:130%;
        margin-top:1em; margin-bottom:0em;
        }
        H1.top{
        font-size:130%;
        margin-top:0em; margin-bottom:0em;
        }

        H2 {
        font-size:120%;
        margin-top:1em; margin-bottom:.5em;
        }

        H3 {
        font-size:110%;
        margin-top:1em; margin-bottom:0em;
        margin-left:1em;
        }

        H4 {
        font-size:100%;
        margin-top:.8em; margin-bottom:0em;
        margin-left:2em;
        }

        img
        {
        border: none;
        }


        p {
        margin-top:.5em;
        margin-bottom: .5em;
        }

        li p {
        margin-top: .6em;
        margin-bottom: 0em;
        }

        big {
        font-weight: bold;
        font-size: 105%;
        }

        ol {
        margin-top: .5em;
        margin-bottom: 0em;
        }

        ol li {
        padding-bottom: .3em;
        margin-left: -0.5em;
        }

        ul {
        margin-top: .6em;
        margin-bottom: 0em;
        margin-left: 2.75em;
        }

        ol ul
        {
        margin-top: 0.2em;
        margin-left: 2.5em;
        }

        ul li {
        padding-bottom: .3em;
        margin-top: auto;
        margin-left: -1.25em;
        }

        dl ul { /*list item inside a def/term*/
        margin-top: 2em;
        margin-bottom: 0em;
        }

        dl {
        margin-top: -1em;
        }

        ol dl { /*term/def list inside a numbered list*/
        margin-top: -1.5em;
        margin-left: 0em;
        }

        ol dl dl { /*term/def list inside a term/def list*/
        margin-top: 0em;
        margin-left: .2em;
        }

        dd { /*not currently working*/
        margin-bottom: 0em;
        margin-left: 1.5em;
        }

        dt {
        padding-top: 2em;
        font-weight: bold;
        margin-left: 1.5em;
        }

        code {
        font-size: 133%;
        font-family: Courier, monospace;
        }
        kbd {
        font-family: Courier, monospace;
        font-size: 125%;
        color: Navy;
        background-color: White;
        }

        pre {
        margin-top: 0em;
        margin-bottom: 1.5em;
        font-family: Courier, monospace;
        font-size: 125%;
        clear: both;
        }

        pre.code {
        border: #e5e5e5 1px solid;
        background-color: #f8f8f8;
        padding: 2px;
        margin-top: 2px;
        margin-bottom: 2px;
        font-family: "Consolas", "Monaco", "Bitstream Vera Sans Mono", "Courier New",
        Courier, monospace;
        font-size: 100%;
        clear: both;
        }

        table {
        font-size: 100%;
        margin-top: 0px;
        margin-bottom: 0px;
        padding-top: 0px;
        border: 0px;
        padding-bottom: 0px;
        }

        th.center {
        text-align: center;
        }

        th {
        text-align: left;
        background: #dddddd;
        margin: 3pt;
        vertical-align: bottom;
        color: Black;
        }

        tr {
        vertical-align: top;
        font-size: 100%;
        }

        td {
        vertical-align: top;
        font-size: 100%;
        margin-top: 0px;
        margin-bottom: 0px;
        padding-top: 0px;
        padding-bottom: 0px;
        }

        td.clsTopMenu
        {
        background-color: #F0F0F0;
        white-space: nowrap;
        vertical-align: middle;
        color: Black;
        }

        SMALL, .txtSmall
        {
        font-size: 80%
        }

        /* multipage navigation */
        table.clsNavigator {
        width: 100%;
        background-color: #F9F9F9;
        border-collapse: collapse;
        }


        table.clsNavigator tr td
        {
        text-align: center;
        white-space: nowrap;
        vertical-align: middle;
        color: Black;
        border: 1px solid white;
        padding: 1px 2px 2px 2px;
        }
        p.Picture
        {
        text-align: center;
        page-break-after: avoid;
        }
        p.PictureCaption
        {
        text-align: center;
        font-weight:bolder;
        }
        p.Reference
        {

        }

        /* TOC */
        .clsTOC {
        margin-left: 0;
        padding-left: 0px;
        }

        a.toc_item, a.toc_item:link, a.toc_item:visited {
        /* color: Black;
        */ text-decoration: none;
        }
        .clsTOC ul {
        margin-top: 0em;
        margin-bottom: 0em;
        list-style: none none;
        margin-left: 0.1em;
        }

        .clsTOC ul ul {
        margin-top: 0em;
        margin-bottom: 0em;
        margin-left: 1.5em;
        font-weight: normal;
        }

        .clsTOC ul li {
        margin-left: 0;
        margin-top: 0em;
        }

        .clsTOC ul ul li {
        margin-left: 0;
        margin-top: 0em;
        }

        .header_item {
        display: none;
        }

        BLOCKQUOTE{ margin-left :4em; margin-top:1em; margin-right:0.2em;}

        HR{ color : Black }

        .epigraph{width:50%; margin-left : 35%;}
    </style>
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

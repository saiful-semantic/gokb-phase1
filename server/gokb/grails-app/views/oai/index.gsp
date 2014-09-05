<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: OAI Interface</title>
</head>
<body>
	<h1 class="page-header">OAI Record Synchronization</h1>
	<div id="mainarea" class="panel panel-default">
		<div class="panel-heading">
			<h3 class="panel-title">OAI Options</h3>
		</div>
		<div class="panel-body">
			<ul>
				<li><g:link controller='oai' action='index' id='packages'
						params="${[verb:'Identify']}">Identify Packages</g:link></li>
				<li><g:link controller='oai' action='index' id='packages'
						params="${[verb:'ListRecords',metadataPrefix:'gokb']}">Get [full] Packages</g:link></li>
				<li><g:link controller='oai' action='index' id='packages'
						params="${[verb:'ListRecords',metadataPrefix:'oai_dc']}">Get [oai_dc] Packages</g:link></li>
			</ul>
		</div>
	</div>
</body>
</html>


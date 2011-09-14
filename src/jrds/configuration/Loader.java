package jrds.configuration;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import jrds.factories.xml.EntityResolver;
import jrds.factories.xml.JrdsNode;

import org.apache.log4j.Logger;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

class Loader {

	static final private Logger logger = Logger.getLogger(Loader.class);

	private final FileFilter filter = new  FileFilter(){
		public boolean accept(File file) {
			return  (! file.isHidden()) && (file.isDirectory()) || (file.isFile() && file.getName().endsWith(".xml"));
		}
	};

	DocumentBuilder dbuilder = null;

	final private Map<ConfigType, Map<String, JrdsNode>> repositories = new HashMap<ConfigType, Map<String, JrdsNode>>(ConfigType.values().length);

	public Loader() throws ParserConfigurationException {
		this(false);
	}

	public Loader(boolean strict) throws ParserConfigurationException {
		DocumentBuilderFactory instance = DocumentBuilderFactory.newInstance();
		instance.setIgnoringComments(true);
		instance.setValidating(strict);
		dbuilder = instance.newDocumentBuilder();
		dbuilder.setEntityResolver(new EntityResolver());
		dbuilder.setErrorHandler(new ErrorHandler() {
			public void error(SAXParseException exception) throws SAXException {
				throw exception;
			}
			public void fatalError(SAXParseException exception) throws SAXException {
				throw exception;
			}
			public void warning(SAXParseException exception) throws SAXException {
				throw exception;
			}
		});

		for(ConfigType t: ConfigType.values()) {
			repositories.put(t, new  HashMap<String, JrdsNode>());
		}
	}

	public Map<ConfigType, Map<String, JrdsNode>> getRepositories(){
		return repositories;
	}

	public Map<String, JrdsNode> getRepository(ConfigType t) {
		return repositories.get(t);
	}

    public void setRepository(ConfigType t, Map<String, JrdsNode> mapnodes) {
        repositories.put(t, mapnodes);
    }

    public void loadPaths(List<URI> list) {
		for(URI u: list) {
			importUrl(u);
		}
	}

	public void importUrl(URI ressourceUri) {
	    URL ressourceUrl = null;
		try {
		    ressourceUrl = ressourceUri.toURL();
			logger.debug("Importing " + ressourceUrl);
			String protocol = ressourceUrl.getProtocol();
			if("file".equals(protocol)) {
				String fileName = ressourceUrl.getFile();
				File imported = new File(ressourceUrl.toURI());
				if(imported.isDirectory())
					importDir(imported);
				else if(fileName.endsWith(".jar"))
					importJar(new JarFile(imported));
			}
			else if("jar".equals(protocol)) {
				JarURLConnection cnx = (JarURLConnection)ressourceUrl.openConnection();
				importJar(cnx.getJarFile());
			}
			else {
				logger.error("ressource " + ressourceUrl + " can't be loaded" );
			}
		} catch (IOException e) {
			logger.error("Invalid URL " + ressourceUrl + ": " + e);
		} catch (URISyntaxException e){
		    logger.error("Invalid URL " + ressourceUrl + ": " + e);
		}
	}

	public void importDir(File path) {
		logger.trace("Importing directory " + path);
		if(! path.isDirectory()) {
			logger.warn(path + " is not a directory");
			return;
		}
		//listFiles can return null
		File[] foundFiles = path.listFiles(filter);
		if(foundFiles == null) {
			logger.error("Failed to import " + path);
			return;
		}
		for(File f: foundFiles) {
			if(f.isDirectory()) {
				importDir(f);
			}
			else {
				try {
					logger.trace("Will import " + f);
					if (! importStream(new FileInputStream(f)))
						logger.warn("Unknown type for " + f);
				} catch (FileNotFoundException e) {
					logger.error("File not found: " + f);
				} catch (SAXParseException e) {
					logger.error("Invalid xml document " + f + " (line " + e.getLineNumber() + "): " + e.getMessage());
				} catch (SAXException e) {
					logger.error("Invalid xml document " + f  + ": " + e);
				} catch (IOException e) {
					logger.error("IO error with " + f + ": " + e);
				}
			}
		}
	}

	public void importJar(JarFile jarfile) throws IOException {
		if(logger.isTraceEnabled())
			logger.trace("Importing jar " + jarfile.getName());
		for(JarEntry je: Collections.list(jarfile.entries())) {
			String name = je.getName();
			if( !je.isDirectory() && name.endsWith(".xml") && (name.startsWith("desc/") || name.startsWith("graph/") || name.startsWith("probe/"))) {
				logger.trace("Will import jar entry " + je);
				try {
					if(! importStream(jarfile.getInputStream(je))) {
						logger.warn("Unknown type " + je + " in jar " + jarfile);
					}
				} catch (SAXParseException e) {
					logger.error("Invalid xml document " + je + " in " + jarfile + " (line " + e.getLineNumber() + "): " + e.getMessage());
				} catch (SAXException e) {
					logger.error("Invalid xml document " + je + " in " + jarfile + ": " + e);
				}
			}
		}
	}

	boolean importStream(InputStream xmlstream) throws SAXException, IOException {
		boolean known = false;
		JrdsNode d = new JrdsNode(dbuilder.parse(xmlstream));
		for(ConfigType t: ConfigType.values()) {
			if(t.memberof(d)) {
				logger.trace("Found a " + t);
				JrdsNode n = d.getChild(t.getNameXpath());
				//We check the Name
				if(n != null && ! "".equals(n.getTextContent().trim())) {
					String name = n.getTextContent().trim();
					Map<String, JrdsNode> rep = repositories.get(t);
					//We warn for dual inclusion, none is loaded, as we don't know the good one
					if(rep.containsKey(name)) {
						logger.error("Dual definition of " + t + " with name " + name);
						rep.remove(name);
					}
					else {
						rep.put(name, d);
						known = true;
					}
				}
				break;
			}
		}
		return known;
	}
}
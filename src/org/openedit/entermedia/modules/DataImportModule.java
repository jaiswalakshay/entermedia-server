package org.openedit.entermedia.modules;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openedit.Data;
import org.openedit.data.PropertyDetail;
import org.openedit.data.PropertyDetails;
import org.openedit.data.PropertyDetailsArchive;
import org.openedit.data.Searcher;
import org.openedit.entermedia.util.CSVReader;

import com.openedit.WebPageRequest;
import com.openedit.entermedia.scripts.Script;
import com.openedit.entermedia.scripts.ScriptLogger;
import com.openedit.entermedia.scripts.ScriptManager;
import com.openedit.page.Page;
import com.openedit.util.FileUtils;
import com.openedit.util.PathUtilities;
import com.openedit.util.URLUtilities;

public class DataImportModule extends DataEditModule
{
	protected ScriptManager fieldScriptManager;

	public ScriptManager getScriptManager()
	{
		return fieldScriptManager;
	}

	public void setScriptManager(ScriptManager inScriptManager)
	{
		fieldScriptManager = inScriptManager;
	}

	public List listImportScripts(WebPageRequest inReq) throws Exception
	{
		String dataroot = inReq.findValue("dataroot");
		List scripts = getPageManager().getChildrenPaths(dataroot + "/import/scripts/", true);
		List pages = new ArrayList();
		Set dups = new HashSet();
		for (Iterator iterator = scripts.iterator(); iterator.hasNext();)
		{
			String path = (String) iterator.next();
			if (!path.endsWith(".xconf"))
			{
				Page page = getPageManager().getPage(path);
				if (!dups.contains(page.getName()))
				{
					pages.add(page);
					dups.add(page.getName());
				}
			}
		}
		inReq.putPageValue("scripts", pages);
		return scripts;
	}

	public void importDataWithScript(WebPageRequest inReq) throws Exception
	{
		String dataroot = inReq.findValue("dataroot");

		String filename = inReq.findValue("scriptname");
		Script script = getScriptManager().loadScript(dataroot + "/import/scripts/" + filename);

		Map variables = new HashMap();
		variables.put("context", inReq);
		variables.put("moduleManager", getModuleManager());

		ScriptLogger logger = new ScriptLogger();
		logger.startCapture();
		variables.put("log", logger);
		try
		{
			getScriptManager().execScript(variables, script);
		}
		finally
		{
			logger.stopCapture();
		}
	}
	/**
	 * @deprecated use @importDataWithScript
	 * @param inReq
	 * @throws Exception
	 */
	public void importData(WebPageRequest inReq) throws Exception
	{
		Searcher searcher = loadSearcher(inReq);

		List data = new ArrayList();
		Page upload = getPageManager().getPage("/" + inReq.findValue("searchhome") + "/temp/import/import.csv");
		Reader reader = upload.getReader();
		try
		{
			boolean done = false;
			CSVReader read = new CSVReader(reader, ',', '\"');

			String[] headers = read.readNext();

			int idcolumn = 0;

			String line = null;
			int rowNum = 0;
			Data question = null;
			List questions = new ArrayList();
			String[] tabs;
			while ((tabs = read.readNext()) != null)
			{

				rowNum++;

				String idCell = tabs[idcolumn];

				// This means we have moved on to a new product
				//natasha 18584
				if (idCell != null)
				{
					if (question == null || !question.getId().equals(idCell))
					{

						Data target = (Data) searcher.searchById(idCell);
						//Data target = null;
						if (target == null)
						{
							for (Iterator iterator = data.iterator(); iterator.hasNext();)
							{
								Data one = (Data) iterator.next();
								if (idCell.equals(one.getId()))
								{
									target = one;
								}
							}

						}
						if (target == null)
						{

							target = searcher.createNewData();
							target.setId(idCell);

						}

						addProperties(searcher, headers, tabs, target);
						if (target.getSourcePath() == null)
						{
							target.setSourcePath(target.getId());
						}
						data.add(target);

					}
				}

			}
			// inErrorLog.add("Processed: " + products.size() + " products");

			// inSearcher.clear();
			// input.delete();

		}
		finally
		{
			FileUtils.safeClose(reader);
		}
		searcher.saveAllData(data, inReq.getUser());
		searcher.reIndexAll();
	}
	/**
	 * @deprecated use @importDataWithScript
	 */
	protected void addProperties(Searcher inSearcher, String[] inHeaders,
			String[] inInTabs, Data inProduct) {

		for (int i = 0; i < inHeaders.length; i++) {
			String header = inHeaders[i];
			
			
			PropertyDetail detail = inSearcher.getPropertyDetails().getDetail(
					header);
			
			String val = inInTabs[i].trim();
			val = URLUtilities.xmlEscape(val);
			if("sourcepath".equals(header)){
				inProduct.setSourcePath(val);
			}
			if (detail != null && val != null && val.length() > 0) {
				
				inProduct.setProperty(detail.getId(), val);
			} else if(val != null && val.length() >0){
				inProduct.setProperty(header, val);
			}
		}

	}
	
	public void saveScript(WebPageRequest inReq) throws Exception
	{
		String dataroot = inReq.findValue("dataroot");
		String filename = inReq.findValue("filename");
		String code = inReq.findValue("scriptcode");

		Page page = getPageManager().getPage(dataroot + "/import/scripts/" + filename);
		getPageManager().saveContent(page, inReq.getUser(), code, "web edit");

	}
	public void createTable(WebPageRequest inReq) throws Exception
	{
		String tablename = inReq.findValue("tablename");
		String catalogid = inReq.findValue("catalogid");
		
		String searchtype = PathUtilities.makeId(tablename);
		searchtype = searchtype.toLowerCase();
		PropertyDetailsArchive archive = getSearcherManager().getPropertyDetailsArchive(catalogid);
		PropertyDetails details = archive.getPropertyDetails(searchtype);
		//will default to defaults
		archive.savePropertyDetails(details, searchtype, inReq.getUser());
		inReq.setRequestParameter("searchtype", searchtype);

	}	
}

/*
 * Created on May 2, 2004
 */
package org.openedit.data.lucene;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.openedit.Data;
import org.openedit.util.DateStorageUtil;

import com.openedit.OpenEditException;
import com.openedit.hittracker.HitTracker;
import com.openedit.hittracker.Term;

/**
 * @author cburkey
 * 
 */
public class LuceneHitTracker extends HitTracker 
{
	private static final Log log = LogFactory.getLog(LuceneHitTracker.class);
	
	//protected transient TopDocs fieldHits;
	protected transient SearcherManager fieldLuceneSearcherManager;
	protected transient Query fieldLuceneQuery;
	protected transient Sort fieldLuceneSort;
	protected Map fieldPages;
	protected Integer fieldSize;
	
	public LuceneHitTracker(SearcherManager inManager, Query inQuery, Sort inSort)
	{
		setLuceneSearcherManager(inManager);
		setLuceneQuery(inQuery);
		setLuceneSort(inSort);
	}

	public int size() 
	{
		if( fieldSize == null )
		{
			getPage(0);
		}
		return fieldSize;
	}

	public Data get(int inCount)
	{
		int page = inCount / getHitsPerPage();
		
		//get the chunk 1
		List<Data> row = getPage(page);

		// 50 - (1 * 40) = 10 relative
		int indexlocation = inCount - ( page * getHitsPerPage() );

		return row.get(indexlocation);
	}
	protected Map<Integer,List<Data>> getPages()
	{
		if (fieldPages == null)
		{
			fieldPages = new HashMap<Integer,List<Data>>();
			//Does this compile?
			//fieldPages = new ReferenceMap(ReferenceMap.HARD,ReferenceMap.SOFT);
		}
		return fieldPages;
	}
	
	protected List<Data> getPage(int inPageNumberZeroBased)
	{
		List<Data> page = getPages().get(inPageNumberZeroBased);
		if( page == null )
		{
			IndexSearcher searcher = getLuceneSearcherManager().acquire();
			try
			{
				int start = getHitsPerPage() * inPageNumberZeroBased;
				int max = start + getHitsPerPage();
				TopDocs docs = null;
				if( getLuceneSort() != null )
				{
					docs = searcher.search( getLuceneQuery(),max ,getLuceneSort() );
				}
				else
				{
					docs = searcher.search( getLuceneQuery(),max);
				}
				fieldSize = docs.totalHits;
				page = new ArrayList<Data>(getHitsPerPage());
				for (int i = 0; start + i < size() && i < getHitsPerPage(); i++)
				{
					int offset = start + i;
					Document doc = searcher.doc( docs.scoreDocs[offset].doc );
					page.add(new DocumentData(doc) );
				}
				if( log.isDebugEnabled() )
				{
					log.info(size() + " total, loaded " + start + " to " + (start+page.size()) + " query:" + getLuceneQuery() + " sort by: " + getLuceneSort() + " " + getSessionId() );
				}
				getPages().put(inPageNumberZeroBased,page);
			}
			catch( Exception ex )
			{
				throw new OpenEditException(ex);
			}
			finally
			{
				try
				{
					getLuceneSearcherManager().release(searcher);
				}
				catch (IOException e)
				{
					//nada
				}
			}
		}
		return page;
	}
	public Collection<String> getSourcePaths()
	{
		List sourcepaths = new ArrayList();
		
			IndexSearcher searcher = getLuceneSearcherManager().acquire();
			try
			{
				int max = Integer.MAX_VALUE;
				TopDocs docs = null;
				if( getLuceneSort() != null )
				{
					docs = searcher.search( getLuceneQuery(),max ,getLuceneSort() );
				}
				else
				{
					docs = searcher.search( getLuceneQuery(),max);
				}
				fieldSize = docs.totalHits;
				for (int i = 0;  i < fieldSize; i++)
				{
					Document doc = searcher.doc( docs.scoreDocs[i].doc );
					sourcepaths.add( doc.get("sourcepath") );
				}
				log.info(size() + " total query:" + getLuceneQuery() + " session:" + getSessionId() );
				return sourcepaths;
			}
			catch( Exception ex )
			{
				throw new OpenEditException(ex);
			}
			finally
			{
				try
				{
					getLuceneSearcherManager().release(searcher);
				}
				catch (IOException e)
				{
					//nada
				}
			}
	}

	public Iterator iterator() {
		return new HitIterator(this);
	}

	public String toDate(String inValue) 
	{
		if (inValue == null) {
			return null;
		}
		Date date = toDateObject(inValue);
		return DateStorageUtil.getStorageUtil().formatForStorage(date);
	}

	//This is main date API
	public Date getDateValue(Data inHit, String inField)
	{
		String value = inHit.get(inField);
		if( value == null)
		{
			return null;
		}
		return toDateObject(value);
	}
	public Date toDateObject(String inValue)
	{
		Date date = null;
		try {
			date = DateTools.stringToDate(inValue);
		} catch (ParseException ex) {
			log.error(ex);
			return null;
		}
		return date;
	}
	/**
	 * @deprecated removed for $context.getDateTime
	 * @param inValue
	 * @return
	 */
	public String toDateTime(String inValue)
	{
		return toDate(inValue);
	}
	
	//Only look for data within the current page
	public Integer findSelf(String inId) throws Exception {
		if (inId == null) {
			return null;
		}
		int i = getPage() * getHitsPerPage();
		for (Iterator iterator = getPageOfHits().iterator(); iterator.hasNext();)
		{
			i++;
			Data type = (Data) iterator.next();
			if (inId.equals(type.getId() ) )
			{
				return new Integer(i);
			}
		}
		return null;
	}

	public String previousId(String inId) throws Exception 
	{
		Data previous = previous(inId);
		if (previous != null)
		{
			return previous.get("id");
		}
		return null;
	}

	public String nextId(String inId) throws Exception 
	{
		Data next = next(inId);
		if (next != null)
		{
			return next.get("id");
		}
		return null;
	}
	
	public Data previous(String inId) throws Exception
	{
		Integer row = findSelf(inId);
		if (row != null && row.intValue() - 1 >= 0) {
			Data hit = (Data) get(row.intValue() - 1);
			return hit;
		}
		return null;
	}
	
	public Data next(String inId) throws Exception
	{
		Integer row = findSelf(inId);
		if (row != null && row.intValue() + 1 < getTotal()) {
			Data hit =  get(row.intValue() + 1);
			return hit;
		}
		return null;
	}
	//Never call this!!!
	public boolean contains(Object inHit) 
	{
		Data contains = (Data)inHit;
		for (Iterator iterator = iterator(); iterator.hasNext();)
		{
			Data type = (Data) iterator.next();
			String id = type.getId();
			if (id != null && id.equals( contains.getId() ) ) {
				return true;
			}
		}
		return false;
	}

	public String highlight(Object inDoc, String inField) {
		Data doc = (Data) inDoc;
		String value = doc.get(inField);
		if (value != null) {
			for (Iterator iterator = getSearchQuery().getTerms().iterator(); iterator
					.hasNext();) {
				Term term = (Term) iterator.next();
				if (term.getValue() != null) {
					value = replaceAll(value, term.getValue(),
							"<span class='hit'>", "</span>");
				}
			}
		}
		value = trim(value, 300);

		return value;

		// String FIELD_NAME = "text";
		// Highlighter highlighter = new Highlighter(new MyBolder(),
		// new QueryScorer(query));
		// highlighter.setTextFragmenter(new SimpleFragmenter(20));
		// for (int i = 0; i < hits.length(); i++) {
		// System.out.println("URL " + (i + 1) + ": " +
		// hits.doc(i).getField("URL").stringValue());
		// String text = hits.doc(i).get(FIELD_NAME);
		// int maxNumFragmentsRequired = 2;
		// String fragmentSeparator = "...";
		// TokenStream tokenStream =
		// analyzer.tokenStream(FIELD_NAME, new StringReader(text));
		//	
		// String result =
		// highlighter.getBestFragments(
		// tokenStream,
		// text,
		// maxNumFragmentsRequired,
		// fragmentSeparator);
		// System.out.println("\t" + result);
	}

	private String replaceAll(String inSource, String inFind,
			String inPreReplace, String inPostReplace) {
		String lowercase = inSource.toLowerCase();
		String findlower = inFind.toLowerCase();
		StringBuffer buffer = new StringBuffer();
		int start = 0;
		while (true) {
			int hit = lowercase.indexOf(findlower, start);
			if (hit == -1) {
				buffer.append(inSource.substring(start, inSource.length()));
				break;
			}
			String before = inSource.substring(start, hit);
			buffer.append(before);
			buffer.append(inPreReplace);
			String existing = inSource.substring(hit, hit + findlower.length());
			buffer.append(existing);
			buffer.append(inPostReplace);

			start = hit + findlower.length();
		}
		return buffer.toString();
	}

	public String trim(String value, int inMax) {
		if (value.length() > inMax) {
			// trim the begining
			int start = value.indexOf("<span class='hit'>");
			if (start > -1 && start > 10 && start + 150 < value.length()) {
				int before = value.indexOf(" ", start - 10);
				if (before < start) {
					value = value.substring(before, value.length());
				} else {

				}
				// TODO: Look for needed <span class='res'>
			}
			// if still too long trim the end
			if (value.length() > inMax) {
				int end = inMax;
				// Near max distance.
				for (; end > 0; end--) {
					if (value.charAt(end) == ' ') {
						break;
					}
				}
				value = value.substring(0, end);
				if (value.endsWith("<span")) {
					value = value.substring(0, value.length() - 5);
				}
			}
			// TODO: check for the need for </span>
		}
		return value;
	}

	public String getValue(Object inHit, String inKey)
	{
		if(inHit instanceof Data)
		{
			Data hit = (Data) inHit;
			return hit.get(inKey);
		}

		if( inHit instanceof Document)
		{
			Document doc = (Document)inHit;
			return doc.get(inKey);				
		}
		else
		{
			log.error("Invalid data type " + inHit);
		}

		return  null;
		
	}
	
	public Data toData(Object inHit)
	{
		if( inHit instanceof Data)
		{
			return (Data)inHit;
		}
		DocumentData data = new DocumentData((Document)inHit);
		return data;
	}
	
	public Object[] toArray()
	{
		List list = new ArrayList(size());
		for (Iterator iterator = iterator(); iterator.hasNext();) {
			Object hit = iterator.next();
			list.add(hit);
		}
		return list.toArray();
	}
//	public Object getById(String inId)
//	{
//		if(inId == null)
//		{
//			return null;
//		}
//		int size = size();
//		for (int i = 0; i < size; i++)
//		{
//			Document doc = getDoc(i);
//			String id = doc.get("id");
//			if( inId.equals(id))
//			{
//				return toData(doc);
//			}
//		}
//		return null;
		
//	}
	
	public SearcherManager getLuceneSearcherManager()
	{
		return fieldLuceneSearcherManager;
	}


	public void setLuceneSearcherManager(SearcherManager inLuceneSearcherManager)
	{
		fieldLuceneSearcherManager = inLuceneSearcherManager;
	}



	public Query getLuceneQuery()
	{
		return fieldLuceneQuery;
	}


	public void setLuceneQuery(Query inLuceneQuery)
	{
		fieldLuceneQuery = inLuceneQuery;
	}


	public Sort getLuceneSort()
	{
		return fieldLuceneSort;
	}


	public void setLuceneSort(Sort inLuceneSort)
	{
		fieldLuceneSort = inLuceneSort;
	}


}

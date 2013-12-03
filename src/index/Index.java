package index;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.Version;
import preprocessing.Question;
import preprocessing.QuestionParser;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class Index {

    public static double RAM_BUFFER_SIZE_MB = 4096;
    public static int BATCH_SIZE = 100000;
    public static int NUM_QUESTIONS = 603419; //6034195

    protected File indexFile;
    protected Directory directory;
    protected Analyzer analyzer;
    protected IndexWriterConfig indexWriterConfig;
    protected IndexWriter indexWriter;

    public static void main(String[] args) throws IOException{
        File trainFile = new File(args[0]);
        File outputDir = new File(args[1]);

        if(!trainFile.exists()){
            throw new RuntimeException("Training file doesn't exist");
        }
        if(!outputDir.isDirectory() || !outputDir.exists()){
            throw new RuntimeException("Output directory doesn't exist");
        }

        QuestionParser questionParser = new QuestionParser(trainFile);
        Set<Question> questions = new HashSet<Question>();
        Index index = new Index(outputDir, false);

        double startTime = System.currentTimeMillis();
        for(int i = 0; i <= NUM_QUESTIONS/BATCH_SIZE; i++){
            for(int j = 0; j < BATCH_SIZE; j++){
                questions.add(questionParser.parse());
            }
            index.index(questions);
            questions.clear();
        }
        System.out.println("Indexing took " + (System.currentTimeMillis() - startTime) + "ms");
    }

    /***
     * Creates a new Index.
     * @param indexOutputDirectory Location for Lucene to store the index
     */
    public Index(File indexOutputDirectory, boolean memFlag) throws IOException{
        indexFile = indexOutputDirectory;
        analyzer = new StandardAnalyzer(Version.LUCENE_46);
        indexWriterConfig = new IndexWriterConfig(Version.LUCENE_46, analyzer);
        indexWriterConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        indexWriterConfig.setRAMBufferSizeMB(RAM_BUFFER_SIZE_MB);

        if(memFlag){
            directory = new MMapDirectory(indexFile);
        }
        else {
            directory = FSDirectory.open(indexFile);
        }

        indexWriter = new IndexWriter(directory, indexWriterConfig);
    }

    /***
     * Adds a set of Questions to the index.
     * @param questions
     * @return A set of Questions that were not properly indexed.
     * @throws IOException
     */
    public Set<Question> index(Set<Question> questions) throws IOException {
        Set<Question> failedQuestions = new HashSet<Question>();

        for(Question question : questions){
            try{
                Document doc = new Document();

                FieldType idFieldType = new FieldType();
                idFieldType.setIndexed(false);
                idFieldType.setStored(true);
                idFieldType.setTokenized(false);
                idFieldType.setStoreTermVectors(false);
                idFieldType.setOmitNorms(true);
                doc.add(new Field("id", String.valueOf(question.id), idFieldType));

                FieldType titleFieldType = new FieldType();
                titleFieldType.setIndexed(true);
                titleFieldType.setStored(true);
                titleFieldType.setTokenized(true);
                titleFieldType.setStoreTermVectors(true);
                titleFieldType.setOmitNorms(false);
                doc.add(new Field("title", question.title, titleFieldType));

                FieldType bodyFieldType = new FieldType();
                bodyFieldType.setIndexed(true);
                bodyFieldType.setStored(true);
                bodyFieldType.setTokenized(true);
                bodyFieldType.setStoreTermVectors(true);
                bodyFieldType.setOmitNorms(false);
                doc.add(new Field("body", question.text, bodyFieldType));

                FieldType tagsFieldType = new FieldType();
                tagsFieldType.setIndexed(true);
                tagsFieldType.setStored(true);
                tagsFieldType.setTokenized(false);
                tagsFieldType.setStoreTermVectors(true);
                tagsFieldType.setOmitNorms(true);
                doc.add(new Field("tags", question.tags, tagsFieldType));

                indexWriter.addDocument(doc);
            } catch (Exception e){
                System.out.println("Failed to add Question " + question.id);
                failedQuestions.add(question);
            }
        }

        try{
            indexWriter.commit();
        } catch (Exception e){
            System.out.println("Failed to close IndexWriter: " + e.getMessage());
        }

        return failedQuestions;
    }

    public void close() throws IOException{
        indexWriter.close();
    }
}

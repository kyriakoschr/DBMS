import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import java.util.List;
import org.fluttercode.datafactory.impl.DataFactory;

public class myFaker {
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Mongo mongo = new Mongo("localhost",27017);
        DB db=mongo.getDB("myDB");
        DBCollection collection = db.getCollection("citizens");
        DBCollection incidents = db.getCollection("incidents");
        
        DataFactory df = new DataFactory();
        
        for (int i = 0; i < 2000; i++) {
            DBCursor cursor= incidents.find().skip(i*1000).limit(1000);
            BasicDBObject citizen = new BasicDBObject ();
            citizen.put("name",df.getFirstName()+" "+df.getLastName());
            citizen.put("telephone", df.getNumberText(10));
            citizen.put("address", df.getAddress()+","+df.getCity()+","+df.getNumberText(5));
            collection.insert(citizen); //kanoume push to citizen
            //System.out.println(cursor.toArray());
            List<DBObject> incList=cursor.toArray();
            //int[] array=new int[2];
            BasicDBObject[] array = new BasicDBObject[1000];
            //for(int j=0;j<2;j++){ //vriskoume ta incidents pou tha kamei upvote kai ta vazoume sto array
            for(int j=0;j<1000;j++){
                //array[j]=((Number)incList.get(j).get("ward")).intValue();
                if(incList.get(j).get("ward")!=null){
                    array[j]=new BasicDBObject();
                    array[j].put("uv_id",incList.get(j).get("_id"));
                    array[j].put("uv_ward",(int)(Number)(incList.get(j).get("ward")));
                }
                //System.out.println(array[j]);
                //array[j]=((Number)cursor.curr().get("ward")).intValue();
                //System.out.println(citizen.get("_id"));
                
                incidents.update(new BasicDBObject("_id",incList.get(j).get("_id")),new BasicDBObject("$push",new BasicDBObject("upvotes",citizen.get("_id"))));
            }
            //System.out.println(array.toString());
            BasicDBObject citizen2 = new BasicDBObject();
            citizen2.append("$set", new BasicDBObject().append("upvotes", array)); //ta nea dedomena pou prepei na mpoun ston citizen
            BasicDBObject searchQuery = new BasicDBObject().append("_id", citizen.get("_id")); //to query gia na vroume to citizen pou theloume
            collection.update(searchQuery,citizen2); //kanoume update to citizen me ta upvotes

        }
    }
}

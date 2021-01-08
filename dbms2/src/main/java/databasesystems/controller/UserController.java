package databasesystems.controller;

import com.mongodb.AggregationOptions;
import com.mongodb.BasicDBList;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.mongodb.BasicDBObject;
import com.mongodb.Cursor;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

@RestController
class UserController {
    
    @Autowired
    MongoTemplate mt;

    @RequestMapping(value="/upvote", method = RequestMethod.POST)
    void upvote(@RequestBody BasicDBObject fields) throws ParseException{
        DBCollection incidents = mt.getDb().getCollection("incidents");
        DBCollection citizens = mt.getDb().getCollection("citizens");
        DBObject citizen=citizens.findOne(new BasicDBObject("name",fields.get("name")));
        ObjectId x=new ObjectId(fields.get("incident").toString());
        DBObject incident=incidents.findOne(new BasicDBObject("_id",x));
        BasicDBList votes= (BasicDBList) citizen.get("upVotes");
        for(int i=0;i<votes.size();i++){
            if(((BasicDBObject)(votes.get(i)))==null)
                continue;
            if(((BasicDBObject)(votes.get(i))).get("uv_id").equals(x))
                return;
        }
        BasicDBObject newVote=new BasicDBObject("uv_id",x);
        newVote.put("uv_ward", incident.get("ward"));
        citizens.update(new BasicDBObject("name",fields.get("name")),new BasicDBObject("$push",new BasicDBObject("upVotes",newVote)));
        incidents.update(new BasicDBObject("_id",x),new BasicDBObject("$push",new BasicDBObject("upvotes",citizen.get("_id"))));
    }
    
    @RequestMapping(value="/add", method = RequestMethod.POST)
    public void add(@RequestBody BasicDBObject fields) throws ParseException{
        DBCollection incidents = mt.getDb().getCollection("incidents");
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd");
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date cr_date=null;
        Date compl_date=null;
        if(fields.get("cr_date")!=null)
            cr_date= isoFormat.parse(fields.get("cr_date").toString());
        if(fields.get("compl_date")!=null)
            compl_date= isoFormat.parse(fields.get("compl_date").toString());
        fields.replace("cr_date", cr_date);
        fields.replace("compl_date", compl_date);
        incidents.insert(fields);
    }

    @GetMapping(value="/query11")
    @ResponseStatus(value = HttpStatus.OK)
    BasicDBObject q11 (@RequestParam String name) throws IOException, ParseException {
        DBCollection incidents = mt.getDb().getCollection("citizens");
        
        DBObject match = new BasicDBObject ("$match",new BasicDBObject ("name",name));
        DBObject unwind = new BasicDBObject ("$unwind", "$upVotes");
        DBObject groupFields = new BasicDBObject("_id", "$upVotes.uv_ward"); //groupby        
        DBObject group = new BasicDBObject("$group", groupFields);

        List<DBObject> pipeline = Arrays.asList(match, unwind, group);
        
        Cursor cursor=incidents.aggregate(pipeline, AggregationOptions.builder().outputMode(AggregationOptions.OutputMode.CURSOR).build());
        List<DBObject> list=new ArrayList<>();
        while (cursor.hasNext()){
            list.add((DBObject) cursor.next());
        }
        BasicDBObject res=new BasicDBObject();
        res.put("results", list.toArray());
        return res;        
    }
    
    @GetMapping(value="/query10")
    @ResponseStatus(value = HttpStatus.OK)
    BasicDBObject q10(){
        DBCollection citizens = mt.getDb().getCollection("citizens");
        BasicDBObject query=new BasicDBObject();
        query.put("_id",new BasicDBObject( "$ne",null));
        query.put("count",new BasicDBObject( "$gt",1));
        DBObject groupFields = new BasicDBObject( "_id","$telephone");
        groupFields.put("incid",new BasicDBObject( "$addToSet","$upVotes.uv_id"));
        groupFields.put("count",new BasicDBObject( "$sum",1));
        DBObject group = new BasicDBObject("$group", groupFields);
        DBObject match = new BasicDBObject("$match",query);
        BasicDBObject proj=new BasicDBObject("incid",1);
        proj.put("_id",0);
        BasicDBObject project=new BasicDBObject("$project",proj);
        List<DBObject> pipeline = Arrays.asList(group,match,project);

        Cursor cursor=citizens.aggregate(pipeline,  AggregationOptions.builder().outputMode(AggregationOptions.OutputMode.CURSOR).build());
        Set<String> set = new HashSet<>();
        while (cursor.hasNext()){
            BasicDBList temp = (BasicDBList) cursor.next().get("incid");
            for(int i=0;i<temp.size();i++){
                set.add(temp.get(i).toString().split(": \\\"")[1].split("\"")[0]);
            }
        }
        BasicDBObject res=new BasicDBObject();
        res.put("results",set.toArray());
        return res;
    }
    
    @GetMapping(value="/query9")
    @ResponseStatus(value = HttpStatus.OK)
    BasicDBObject q9(){
        DBCollection citizens = mt.getDb().getCollection("citizens");
        BasicDBList sub = new BasicDBList();
        sub.add("$wards");
        sub.add(null);
        BasicDBObject project=new BasicDBObject("$project",new BasicDBObject("size",new BasicDBObject("$size",new BasicDBObject("$ifNull",sub))));
        BasicDBObject proj=new BasicDBObject("$project",new BasicDBObject("size",0));
        DBObject unwind = new BasicDBObject("$unwind","$upVotes");
        DBObject groupFields = new BasicDBObject( "_id","$_id");
        groupFields.put("wards", new BasicDBObject("$addToSet","$upVotes.uv_ward"));
        DBObject group = new BasicDBObject("$group", groupFields);
        DBObject sort = new BasicDBObject("$sort",new BasicDBObject("size",-1));
        
        List<DBObject> pipeline = Arrays.asList(unwind,group,project,sort,proj);

        Cursor cursor=citizens.aggregate(pipeline,  AggregationOptions.builder().outputMode(AggregationOptions.OutputMode.CURSOR).build());
        List<BasicDBObject> list=new ArrayList<>();
        while (cursor.hasNext()){
            list.add((BasicDBObject) cursor.next());
        }
        
        BasicDBObject[] arr=new BasicDBObject[list.size()];
        for(int i=0;i<list.size();i++){
            BasicDBObject temp=(BasicDBObject) list.get(i);
            arr[i]=new BasicDBObject("ID",temp.get("_id").toString());
        }
        BasicDBObject res=new BasicDBObject();
        res.put("results",arr);
        return res;
    }
    
    @GetMapping(value="/query8")
    @ResponseStatus(value = HttpStatus.OK)
    BasicDBObject q8(){
        DBCollection citizens = mt.getDb().getCollection("citizens");
        BasicDBObject proj=new BasicDBObject();
        BasicDBObject limit=new BasicDBObject("$limit",50);
        BasicDBObject sorting=new BasicDBObject();
        DBObject unwind = new BasicDBObject("$unwind","$upVotes");
        BasicDBObject newJ=new BasicDBObject("ID","$_id");
        newJ.put("Name","$name");
        DBObject groupFields = new BasicDBObject( "_id",newJ);
        groupFields.put("count", new BasicDBObject("$sum",1));
        DBObject group = new BasicDBObject("$group", groupFields);
        sorting.put("count",-1);
        proj.put("name", 1);
        proj.put("upVotes", 1);
        DBObject sort = new BasicDBObject("$sort",sorting);
        
        List<DBObject> pipeline = Arrays.asList(unwind,group,sort,limit);

        Cursor cursor=citizens.aggregate(pipeline,  AggregationOptions.builder().outputMode(AggregationOptions.OutputMode.CURSOR).build());
        List<DBObject> list=new ArrayList<>();
        while (cursor.hasNext()){
            list.add((DBObject) cursor.next());
        }
        BasicDBObject[] arr=new BasicDBObject[list.size()];
        for(int i=0;i<list.size();i++){
            BasicDBObject temp=(BasicDBObject) list.get(i);
            BasicDBObject tstring = (BasicDBObject) temp.get("_id");
            arr[i]=new BasicDBObject(tstring.getString("Name"),temp.get("count"));
        }
        BasicDBObject res=new BasicDBObject();
        res.put("results",arr);
        return res;
    }

    @GetMapping(value="/query7")
    @ResponseStatus(value = HttpStatus.OK)
    BasicDBObject q7(@RequestParam String day) throws ParseException{
        DBCollection incidents = mt.getDb().getCollection("incidents");
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd");
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        Date date = isoFormat.parse(day);
        BasicDBObject query=new BasicDBObject("cr_date",date);
        BasicDBObject proj=new BasicDBObject();
        BasicDBObject limit=new BasicDBObject("$limit",50);
        BasicDBObject sorting=new BasicDBObject();
        DBObject unwind = new BasicDBObject("$unwind","$upvotes");
        DBObject groupFields = new BasicDBObject( "_id","$_id");
        groupFields.put("count", new BasicDBObject("$sum",1));
        DBObject group = new BasicDBObject("$group", groupFields);
        sorting.put("count",-1);
        proj.put("name", 1);
        proj.put("upVotes", 1);
        BasicDBObject match=new BasicDBObject("$match",query);
        DBObject sort = new BasicDBObject("$sort",sorting);
        
        List<DBObject> pipeline = Arrays.asList(match,unwind,group,sort,limit);

        Cursor cursor=incidents.aggregate(pipeline,  AggregationOptions.builder().outputMode(AggregationOptions.OutputMode.CURSOR).build());
        List<DBObject> list=new ArrayList<>();
        while (cursor.hasNext()){
            list.add((DBObject) cursor.next());
        }
        BasicDBObject[] arr=new BasicDBObject[list.size()];
        for(int i=0;i<list.size();i++){
            BasicDBObject temp=(BasicDBObject) list.get(i);
            arr[i]=new BasicDBObject(temp.getString("_id"),temp.get("count"));
        }
        BasicDBObject res=new BasicDBObject();
        res.put("results",arr);
        return res;
    }
    
    @GetMapping(value="/query6")
    @ResponseStatus(value = HttpStatus.OK)
    BasicDBObject q6(@RequestParam String day,@RequestParam String fromx,@RequestParam String fromy,
                    @RequestParam String tox,@RequestParam String toy) throws IOException, ParseException {
        DBCollection incidents = mt.getDb().getCollection("incidents");
        BasicDBObject query=new BasicDBObject();
        BasicDBObject rangex=new BasicDBObject();
        BasicDBObject rangey=new BasicDBObject();
        BasicDBObject limit=new BasicDBObject("$limit",1);
        BasicDBObject sorting=new BasicDBObject();
        Date date;
                
        SimpleDateFormat isoFormat =new SimpleDateFormat("yyyy-MM-dd");
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    
        date = isoFormat.parse(day);              
        
        query.put("cr_date", date);
        Float fx= new Float(fromx);
        Float fy= new Float(fromy);
        Float tx= new Float(tox);
        Float ty= new Float(toy);
        rangex.put("$gt",fx);
        rangex.put("$lt",tx);
        rangey.put("$gt",fy);
        rangey.put("$lt",ty);
        query.put("lat", rangex);
        query.put("lon", rangey);
        sorting.put("count",-1);
        DBObject match = new BasicDBObject("$match",query);
        DBObject project = new BasicDBObject("$project",new BasicDBObject("type", 1));
        DBObject groupFields = new BasicDBObject( "_id", "$type");
        DBObject sort = new BasicDBObject("$sort",sorting);
        groupFields.put("count", new BasicDBObject("$sum",1));
        DBObject group = new BasicDBObject("$group", groupFields);
        List<DBObject> pipeline = Arrays.asList(match,project, group,sort,limit);
        Cursor cursor=incidents.aggregate(pipeline,AggregationOptions.builder().outputMode(AggregationOptions.OutputMode.CURSOR).build());
        BasicDBObject res=new BasicDBObject();
        res.put("results",cursor.next().get("_id"));
        return res;    
    }
    
  @GetMapping(value="/query5")
    @ResponseStatus(value = HttpStatus.OK)
    BasicDBObject q5 (@RequestParam String d1,@RequestParam String d2) throws IOException, ParseException {
        DBCollection incidents = mt.getDb().getCollection("incidents");
        BasicDBObject query=new BasicDBObject();
        BasicDBObject range=new BasicDBObject();
        
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd");
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        Date start = isoFormat.parse(d1);
        Date end = isoFormat.parse(d2);
        
        range.put("$gte",start );
        range.put("$lte", end );
        
        query.put("compl_date", new BasicDBObject("$ne",null));
        query.put("cr_date", range);
        
        BasicDBList sub = new BasicDBList();
        sub.add("$compl_date");
        sub.add("$cr_date");
        
        DBObject match = new BasicDBObject ("$match", query);
        DBObject subtract = new BasicDBObject ("$subtract", sub);
        DBObject groupFields = new BasicDBObject("_id", "$type"); //groupby
        groupFields.put("avg", new BasicDBObject("$avg",subtract));
        
        DBObject group = new BasicDBObject("$group", groupFields);
        List<DBObject> pipeline = Arrays.asList(match, group);
        
        Cursor cursor=incidents.aggregate(pipeline, AggregationOptions.builder().outputMode(AggregationOptions.OutputMode.CURSOR).build());
        
        List<DBObject> list=new ArrayList<>();
        while (cursor.hasNext()){
            list.add((DBObject) cursor.next());
        }
        BasicDBObject[] arr=new BasicDBObject[list.size()];
        for (int i=0; i<list.size(); i++) {
            BasicDBObject temp = (BasicDBObject) list.get(i);
            arr[i]=new BasicDBObject (temp.get("_id").toString(), Double.parseDouble(temp.get("avg").toString())/(60*60*24*1000));
        }
                
        BasicDBObject res=new BasicDBObject();
        res.put("results", arr);
        return res;    
    }
    
    @GetMapping(value="/query4")
    @ResponseStatus(value = HttpStatus.OK)
    BasicDBObject q4(@RequestParam String type) throws IOException, ParseException {
        DBCollection incidents = mt.getDb().getCollection("incidents");
        BasicDBObject query=new BasicDBObject();
        BasicDBObject proj=new BasicDBObject();
        BasicDBObject sorting=new BasicDBObject();
        BasicDBObject limit=new BasicDBObject("$limit",3);
        BasicDBObject wards=new BasicDBObject("$exists","true");
        wards.put("$ne",null);
        query.put("type", type);
        query.put("ward", wards);
        proj.put("ward", 1);
        sorting.put("count",1);
        DBObject match = new BasicDBObject("$match",query);
        DBObject sort = new BasicDBObject("$sort",sorting);
        DBObject project = new BasicDBObject("$project",proj);
        DBObject groupFields = new BasicDBObject( "_id", "$ward");
        groupFields.put("count", new BasicDBObject("$sum",1));
        DBObject group = new BasicDBObject("$group", groupFields);
        List<DBObject> pipeline = Arrays.asList(match,project, group,sort,limit);
        
        Cursor cursor=incidents.aggregate(pipeline,  AggregationOptions.builder().outputMode(AggregationOptions.OutputMode.CURSOR).build());
        List<DBObject> list=new ArrayList<>();
        while (cursor.hasNext()){
            list.add((DBObject) cursor.next());
        }
        BasicDBObject[] arr=new BasicDBObject[list.size()];
        for(int i=0;i<list.size();i++){
            BasicDBObject temp=(BasicDBObject) list.get(i);
            arr[i]=new BasicDBObject(temp.get("_id").toString(),temp.get("count"));
        }
        BasicDBObject res=new BasicDBObject();
        res.put("results",arr);
        return res;
    }
    
    @GetMapping(value="/query3")
    @ResponseStatus(value = HttpStatus.OK)
    BasicDBObject q3 (@RequestParam String d1) throws IOException, ParseException {
        DBCollection incidents = mt.getDb().getCollection("incidents");
        BasicDBList sub = new BasicDBList();
        sub.add("$types.Type");
        sub.add(3);
        BasicDBObject query=new BasicDBObject();
        BasicDBObject slice=new BasicDBObject("$slice",sub);
        
        BasicDBObject pr1=new BasicDBObject("_id",0);
        pr1.put("ZipCode", "$_id");
        pr1.put("types", slice);
        BasicDBObject project=new BasicDBObject("$project",pr1);
        
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd");
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date spec_date = isoFormat.parse(d1);
        
        query.put("cr_date", spec_date);
        query.put("zip", new BasicDBObject("$ne",null));
        DBObject match = new BasicDBObject("$match",query);
        
        BasicDBObject jsn = new BasicDBObject("zip", "$zip");
        BasicDBObject jsn3 = new BasicDBObject("Type", "$_id.type");
        jsn3.put("Count","$count");
        BasicDBObject jsn2 = new BasicDBObject("$push", jsn3);
        jsn.put("type", "$type");
        DBObject groupFields = new BasicDBObject("_id", jsn); //groupby
        DBObject groupFields2 = new BasicDBObject("_id", "$_id.zip"); //groupby
        groupFields2.put("types", jsn2);
        groupFields.put("count", new BasicDBObject("$sum",1));
        DBObject group1 = new BasicDBObject("$group", groupFields);
        DBObject group2 = new BasicDBObject("$group", groupFields2);
        BasicDBObject p = new BasicDBObject();
        p.put("type", 1);
        p.put("zip", 1);
        DBObject sort1 = new BasicDBObject ("$sort", new BasicDBObject("count", -1));        
        DBObject sort2 = new BasicDBObject ("$sort", new BasicDBObject("_id", 1));        
        
        List<DBObject> pipeline = Arrays.asList(match, group1, sort1,group2,project,sort2);
        
        Cursor cursor=incidents.aggregate(pipeline, AggregationOptions.builder().outputMode(AggregationOptions.OutputMode.CURSOR).build());
        
        List<DBObject> list=new ArrayList<>();
        while (cursor.hasNext()){
            list.add((DBObject) cursor.next());
        }
        BasicDBObject res=new BasicDBObject();
        res.put("results", list.toArray());
        return res;    
    }
    
    @GetMapping(value="/query2")
    @ResponseStatus(value = HttpStatus.OK)
    BasicDBObject q2(@RequestParam String type,@RequestParam String d1,@RequestParam String d2) throws IOException, ParseException {
        DBCollection incidents = mt.getDb().getCollection("incidents");
        BasicDBObject query=new BasicDBObject();
        BasicDBObject range=new BasicDBObject();
        Date from;
        Date to;
                
        SimpleDateFormat isoFormat =new SimpleDateFormat("yyyy-MM-dd");
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    
        from = isoFormat.parse(d1);              
        to = isoFormat.parse(d2);              
        query.put("type", type);
        range.put("$gt",from );
        range.put("$lt",to );
        query.put("cr_date", range);
        
        DBObject match = new BasicDBObject("$match",query);
        DBObject project = new BasicDBObject("$project",new BasicDBObject("cr_date", 1));
        DBObject groupFields = new BasicDBObject( "_id", "$cr_date");
        groupFields.put("count", new BasicDBObject("$sum",1));
        DBObject group = new BasicDBObject("$group", groupFields);
        List<DBObject> pipeline = Arrays.asList(match,project, group);
        
        Cursor cursor=incidents.aggregate(pipeline,  AggregationOptions.builder().outputMode(AggregationOptions.OutputMode.CURSOR).build());
        List<DBObject> list=new ArrayList<>();
        while (cursor.hasNext()){
            list.add((DBObject) cursor.next());
        }
        BasicDBObject[] arr=new BasicDBObject[list.size()];
        for(int i=0;i<list.size();i++){
            BasicDBObject temp=(BasicDBObject) list.get(i);
            arr[i]=new BasicDBObject(temp.get("_id").toString(),temp.get("count"));
        }
        BasicDBObject res=new BasicDBObject();
        res.put("results",arr);
        return res;

    }
    
    @GetMapping(value="/query1")
    @ResponseStatus(value = HttpStatus.OK)
    BasicDBObject q1 (@RequestParam String d1, @RequestParam String d2) throws IOException, ParseException {
        DBCollection incidents = mt.getDb().getCollection("incidents");
        BasicDBObject query=new BasicDBObject();
        BasicDBObject range=new BasicDBObject();
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd");
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        Date start = isoFormat.parse(d1);
        Date end = isoFormat.parse(d2);
        
        range.put("$gt",start );
        range.put("$lt", end );
        
        query.put("cr_date", range);
        DBObject match = new BasicDBObject("$match",query);
        DBObject groupFields = new BasicDBObject("_id", "$type"); //groupby
        groupFields.put("count", new BasicDBObject("$sum",1));
        DBObject group = new BasicDBObject("$group", groupFields);
        BasicDBObject p = new BasicDBObject();
        p.put("type", 1);
        //p.put("count", 1);
        DBObject projection = new BasicDBObject ("$project", p);
        DBObject sort = new BasicDBObject ("$sort", new BasicDBObject("count", -1));
        
        List<DBObject> pipeline = Arrays.asList(match, projection, group, sort);
        
        Cursor cursor=incidents.aggregate(pipeline, AggregationOptions.builder().outputMode(AggregationOptions.OutputMode.CURSOR).build());
        List<DBObject> list=new ArrayList<>();
        while (cursor.hasNext()){
            list.add((DBObject) cursor.next());
        }
        BasicDBObject[] arr=new BasicDBObject[list.size()];
        for (int i=0; i<list.size(); i++) {
            BasicDBObject temp = (BasicDBObject) list.get(i);
            arr[i]=new BasicDBObject (temp.get("_id").toString(), temp.get("count"));
        }
        BasicDBObject res=new BasicDBObject();
        res.put("results", arr);
        return res;
    }
    
}
package org.apache.iotdb.tsfile.encoding;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Objects;

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.sort;

public class ReorderingEncodeTest {

  static int DeviationOutlierThreshold = 8;
  static int OutlierThreshold = 0;
  public static int zigzag(int num){
    if(num<0){
      return 2*(-num)-1;
    }else{
      return 2*num;
    }
  }

  public static int max3(int a,int b,int c){
    if(a>=b && a >=c){
      return a;
    }else if(b >= a && b >= c){
      return b;
    }else{
      return c;
    }
  }
  public static int getBitWith(int num){
    return 32 - Integer.numberOfLeadingZeros(num);
  }
  public static byte[] int2Bytes(int integer)
  {
    byte[] bytes = new byte[4];
    bytes[0] = (byte) (integer >> 24);
    bytes[1] = (byte) (integer >> 16);
    bytes[2] = (byte) (integer >> 8);
    bytes[3] = (byte) integer;
    return bytes;
  }
  public static byte[] bitPacking(ArrayList<Integer> numbers,int bit_width){
    int block_num = numbers.size()/8;
//    System.out.println(bit_width);
    byte[] result = new byte[bit_width*block_num];
    for(int i=0;i<block_num;i++){
      for(int j=0;j<bit_width;j++){
        int tmp_int = 0;
        for(int k=0;k<8;k++){
          tmp_int += (((numbers.get(i*8+k) >>j) %2) << k);
        }
//             System.out.println(Integer.toBinaryString(tmp_int));
        result[i*bit_width+j] = (byte) tmp_int;
      }
    }
    return result;
  }
  public static byte[] bitPacking(ArrayList<ArrayList<Integer>> numbers,int index,int bit_width){
    int block_num = numbers.size()/8;
    byte[] result = new byte[bit_width*block_num];
    for(int i=0;i<block_num;i++){
      for(int j=0;j<bit_width;j++){
        int tmp_int = 0;
        for(int k=0;k<8;k++){
          tmp_int += (((numbers.get(i*8+k+1).get(index) >>j) %2) << k);
        }
//        System.out.println(Integer.toBinaryString(tmp_int));
        result[i*bit_width+j] = (byte) tmp_int;
      }
    }
    return result;
  }
  public static void quickSort(ArrayList<ArrayList<Integer>> ts_block, int index, int low, int high) {
    if(low>=high)
      return;
    ArrayList<Integer> pivot = ts_block.get(low);
    int l = low;
    int r = high;
    ArrayList<Integer> temp;
    while(l<r){
      while (l < r && ts_block.get(r).get(index) >= pivot.get(index)) {
        r--;
      }
      while (l < r && ts_block.get(l).get(index) <= pivot.get(index)) {
        l++;
      }
      if (l < r) {
        temp = ts_block.get(l);
        ts_block.set(l, ts_block.get(r));
        ts_block.set(r, temp);
      }
    }
    ts_block.set(low, ts_block.get(l));
    ts_block.set(l, pivot);
    if (low < l) {
      quickSort(ts_block,index, low, l - 1);
    }
    if (r < high) {
      quickSort(ts_block,index, r + 1, high);
    }
  }
  public static void quickSort2(ArrayList<ArrayList<Integer>> ts_block, int low, int high) {
    if(low>=high)
      return;
    ArrayList<Integer> pivot = ts_block.get(low);
    int l = low;
    int r = high;
    ArrayList<Integer> temp;
    while(l<r){
      while (l < r && (ts_block.get(r).get(2) > pivot.get(2)||
              (Objects.equals(ts_block.get(r).get(2), pivot.get(2)) &&ts_block.get(r).get(1) >= pivot.get(1)))) {
        r--;
      }
      while (l < r && ts_block.get(l).get(2) < pivot.get(2)||
              (Objects.equals(ts_block.get(l).get(2), pivot.get(2)) &&ts_block.get(l).get(1) < pivot.get(1))) {
        l++;
//        System.out.println(l);
      }
      if (l < r) {
        temp = ts_block.get(l);
        ts_block.set(l, ts_block.get(r));
        ts_block.set(r, temp);
      }
    }
    ts_block.set(low, ts_block.get(l));
    ts_block.set(l, pivot);
    if (low < l) {
      quickSort2(ts_block, low, l - 1);
    }
    if (r < high) {
      quickSort2(ts_block,r + 1, high);
    }
  }

  public static void quickSort22(ArrayList<ArrayList<Integer>> ts_block, int low, int high) {
    if(low>=high)
      return;
    ArrayList<Integer> pivot = ts_block.get(low);
    int l = low;
    int r = high;
    ArrayList<Integer> temp;
    while(l<r){
      while (l < r && (ts_block.get(r).get(0) > pivot.get(0)||
              (Objects.equals(ts_block.get(r).get(0), pivot.get(0)) &&ts_block.get(r).get(1) >= pivot.get(1)))) {
        r--;
      }
      while (l < r && ts_block.get(l).get(0) < pivot.get(0)||
              (Objects.equals(ts_block.get(l).get(0), pivot.get(0)) &&ts_block.get(l).get(1) < pivot.get(1))) {
        l++;
//        System.out.println(l);
      }
      if (l < r) {
        temp = ts_block.get(l);
        ts_block.set(l, ts_block.get(r));
        ts_block.set(r, temp);
      }
    }
    ts_block.set(low, ts_block.get(l));
    ts_block.set(l, pivot);
    if (low < l) {
      quickSort22(ts_block, low, l - 1);
    }
    if (r < high) {
      quickSort22(ts_block,r + 1, high);
    }
  }

  public static void splitTimeStamp(ArrayList<ArrayList<Integer>> ts_block, int block_size, int td,
                                    ArrayList<Integer> deviation_list,ArrayList<Integer> result){
    int deviation_max = Integer.MIN_VALUE;
    int max_bit_width_deviation;
    int r0;
    int d0;

    // split timestamp into intervals and deviations

    //address other timestamps and values
    for(int j=block_size-1;j>0;j--) {
      int delta_interval = (ts_block.get(j).get(0) - ts_block.get(j-1).get(0))/td;
      ArrayList<Integer> tmp = ts_block.get(j);
      tmp.add(delta_interval);
      ts_block.set(j,tmp);
    }


    // address timestamp0
    r0 = ts_block.get(0).get(0) /td;
    d0 = ts_block.get(0).get(0) %td;
    if(d0 >= (td/2)){
      d0 -= td;
      r0 ++;
    }
    d0 = zigzag(d0);
//    deviation_list.add(d0);
    if(d0 > deviation_max){
      deviation_max = d0;
    }
    ArrayList<Integer> tmp0 = ts_block.get(0);
    tmp0.add(0);
//    System.out.println(tmp0);
    ts_block.set(0,tmp0);

    for(int j=1;j<block_size;j++){
      int interval = ts_block.get(j).get(2) + ts_block.get(j-1).get(2);
      ArrayList<Integer> tmp;
      tmp = ts_block.get(j);
      tmp.set(2,interval);
      ts_block.set(j,tmp);
    }
//    System.out.println(ts_block);
    quickSort2(ts_block,0,block_size-1);

    for(int j=block_size-1;j>0;j--){
      int interval = ts_block.get(j).get(2);
      int value = ts_block.get(j).get(1);

      int delta_interval = ts_block.get(j).get(2) - ts_block.get(j-1).get(2);
      int deviation = (ts_block.get(j).get(0) - ts_block.get(j-1).get(0))-delta_interval*td;
      deviation = zigzag(deviation);
      deviation_list.add(deviation);
      if(deviation > deviation_max){
        deviation_max = deviation;
      }

      ArrayList<Integer> tmp = new ArrayList<>();
      tmp.add(interval);
      tmp.add(value);
      ts_block.set(j,tmp);
    }
    tmp0 = new ArrayList<>();
    tmp0.add(ts_block.get(0).get(2));
    tmp0.add(ts_block.get(0).get(1));
    ts_block.set(0,tmp0);


    max_bit_width_deviation = getBitWith(deviation_max);
    result.add(max_bit_width_deviation);
    result.add(r0);
    result.add(d0);
  }
  public static ArrayList<ArrayList<Integer>> getEncodeBitsDelta(ArrayList<ArrayList<Integer>> ts_block, int block_size,
                                                      ArrayList<Integer> result, ArrayList<Integer> i_star){
    int timestamp_delta_min = Integer.MAX_VALUE;
    int value_delta_min = Integer.MAX_VALUE;
    ArrayList<ArrayList<Integer>> ts_block_delta = new ArrayList<>();

    ArrayList<Integer> tmp0 = new ArrayList<>();
    tmp0.add(ts_block.get(0).get(0));
    tmp0.add(ts_block.get(0).get(1));
    ts_block_delta.add(tmp0);

    // delta to Delta
    for(int j=1;j<block_size;j++) {
      int delta_r = ts_block.get(j).get(0) - ts_block.get(j-1).get(0);
      int delta_v = ts_block.get(j).get(1) - ts_block.get(j-1).get(1);
      ArrayList<Integer> tmp = new ArrayList<>();
      tmp.add(delta_r);
      tmp.add(delta_v);
      ts_block_delta.add(tmp);

      if(delta_r <timestamp_delta_min){
        timestamp_delta_min = delta_r;
      }
      if(delta_v < value_delta_min){
        value_delta_min = delta_v;
      }
    }

    int max_timestamp = Integer.MIN_VALUE;
    int max_timestamp_i = -1;
    int max_value = Integer.MIN_VALUE;
    int max_value_i = -1;
    for(int j=block_size-1;j>0;j--) {
      int ts_2diff_ts = ts_block_delta.get(j).get(0) - timestamp_delta_min;
      int ts_2diff_value = ts_block_delta.get(j).get(1) - value_delta_min;
      ArrayList<Integer> tmp = new ArrayList<>();
      tmp.add(ts_2diff_ts);
      tmp.add(ts_2diff_value);
      ts_block_delta.set(j,tmp);
      if(ts_2diff_ts>max_timestamp){
        max_timestamp = ts_2diff_ts;
        max_timestamp_i = j;
      }
      if(ts_2diff_value>max_value){
        max_value = ts_2diff_value;
        max_value_i = j;
      }
    }
    int max_bit_width_interval = getBitWith(max_timestamp);
    int max_bit_width_value = getBitWith(max_value);
    

    // calculate error
    int  length = (max_bit_width_interval+max_bit_width_value)*(block_size-1);
    result.clear();
    result.add(length);
    result.add(max_bit_width_interval);
    result.add(max_bit_width_value);

    result.add(timestamp_delta_min);
    result.add(value_delta_min);

    i_star.add(max_timestamp_i);
    i_star.add(max_value_i);

    return ts_block_delta;
  }
  public static int getJStar(ArrayList<ArrayList<Integer>> ts_block, int i_star, int block_size,
                                    ArrayList<Integer> raw_length, int index){
    int j_star_bit_width = 33;
    int j_star = 0;
    if(i_star == block_size - 1 || i_star == 0)
      return 0;
    int delta_r_i_star_plus_1 = ts_block.get(i_star+1).get(0) - ts_block.get(i_star-1).get(0);
    int delta_v_i_star_plus_1 = ts_block.get(i_star+1).get(1) - ts_block.get(i_star-1).get(1);
    if(delta_r_i_star_plus_1 > raw_length.get(1) || delta_v_i_star_plus_1 >raw_length.get(2))
      return 0;
    for(int j = 1;j<block_size;j++){
      if(j!=i_star){
        int delta_r_j = ts_block.get(j).get(0) - ts_block.get(i_star).get(0);
        int delta_v_j = ts_block.get(j).get(1) - ts_block.get(i_star).get(1);
        int delta_r_i_star = ts_block.get(i_star).get(0) - ts_block.get(j-1).get(0);
        int delta_v_i_star = ts_block.get(i_star).get(1) - ts_block.get(j-1).get(1);
        if(delta_r_j > raw_length.get(1) || delta_v_j >raw_length.get(2) ||
                delta_r_i_star > raw_length.get(1) || delta_v_i_star >raw_length.get(2)  )
          return 0;
        int max_r = getBitWith(max3(delta_r_i_star_plus_1,delta_r_j,delta_r_i_star));
        int max_v = getBitWith(max3(delta_v_i_star_plus_1,delta_v_j,delta_v_i_star));
        // adjust r
        if(index == 1){
          if(max_v<=raw_length.get(2) && max_r < j_star_bit_width && max_r < raw_length.get(1)){
            j_star_bit_width = max_r;
            j_star = j;
          }
        }else{
          if(max_v<raw_length.get(2) && max_v < j_star_bit_width && max_r <= raw_length.get(1)){
            j_star_bit_width = max_v;
            j_star = j;
          }
        }

      }
    }
    return j_star;
  }
  public static int getIStar(ArrayList<ArrayList<Integer>> ts_block, int block_size,
                             ArrayList<Integer> raw_length, int index){
    int i_star_bit_width = 33;
    int i_star = 0;

    for(int j = 1;j<block_size;j++){
      int epsilon_r_j = getBitWith( ts_block.get(j).get(0) - ts_block.get(j-1).get(0));
      int epsilon_v_j = getBitWith (ts_block.get(j).get(1) - ts_block.get(j-1).get(1));
      if(index == 1){
        if(epsilon_v_j<=raw_length.get(2) && epsilon_r_j < i_star_bit_width && epsilon_r_j < raw_length.get(1)){
          i_star_bit_width = epsilon_r_j;
          i_star = j;
        }
      }else{
        if(epsilon_v_j<raw_length.get(2) && epsilon_v_j < i_star_bit_width && epsilon_r_j <= raw_length.get(1)){
          i_star_bit_width = epsilon_v_j;
          i_star = j;
        }
      }
    }
    return i_star;
  }
  public static ArrayList<Byte> encode2Bytes(ArrayList<ArrayList<Integer>> ts_block,ArrayList<Integer> deviation_list,ArrayList<Integer> raw_length){
    ArrayList<Byte> encoded_result = new ArrayList<>();
//    // encode block size (Integer)
//    byte[] block_size_byte = int2Bytes(ts_block.size());
//    for (byte b : block_size_byte) encoded_result.add(b);

    // encode r0 and d0 of a block (Integer)
    byte[] r0_byte = int2Bytes(raw_length.get(6));
    for (byte b : r0_byte) encoded_result.add(b);
    byte[] d0_byte = int2Bytes(raw_length.get(7));
    for (byte b : d0_byte) encoded_result.add(b);

    // encode min_delta_interval and min_delta_value
    byte[] min_delta_interval_byte = int2Bytes(raw_length.get(3));
    for (byte b : min_delta_interval_byte) encoded_result.add(b);
    byte[] min_delta_value_byte = int2Bytes(raw_length.get(4));
    for (byte b : min_delta_value_byte) encoded_result.add(b);

    // encode interval0 and value0
    byte[] interval0_byte = int2Bytes(ts_block.get(0).get(0));
    for (byte b : interval0_byte) encoded_result.add(b);
    byte[] value0_byte = int2Bytes(ts_block.get(0).get(1));
    for (byte b : value0_byte) encoded_result.add(b);

    // encode interval
    byte[] max_bit_width_interval_byte = int2Bytes(raw_length.get(1));
    for (byte b : max_bit_width_interval_byte) encoded_result.add(b);
    byte[] timestamp_bytes = bitPacking(ts_block,0,raw_length.get(1));
    for (byte b : timestamp_bytes) encoded_result.add(b);

    // encode value
    byte[] max_bit_width_value_byte = int2Bytes(raw_length.get(2));
    for (byte b : max_bit_width_value_byte) encoded_result.add(b);
    byte[] value_bytes = bitPacking(ts_block,1,raw_length.get(2));
    for (byte b : value_bytes) encoded_result.add(b);

    // encode deviation
    byte[] max_bit_width_deviation_byte = int2Bytes(raw_length.get(5));
    for (byte b: max_bit_width_deviation_byte) encoded_result.add(b);
    byte[] deviation_list_bytes = bitPacking(deviation_list,raw_length.get(5));
    for (byte b: deviation_list_bytes) encoded_result.add(b);

    return encoded_result;
  }
  public static ArrayList<Byte> ReorderingDeltaEncoder(ArrayList<ArrayList<Integer>> data,int block_size, int td){
    block_size ++;
    int length_all = data.size();
    int block_num = length_all/block_size;
    ArrayList<Byte> encoded_result=new ArrayList<Byte>();
    byte[] block_size_byte = int2Bytes(block_size);
    for (byte b : block_size_byte) encoded_result.add(b);

    int count_raw = 0;
    int count_reorder = 0;

//    for(int i=0;i<1;i++){
    for(int i=0;i<block_num;i++){
      ArrayList<ArrayList<Integer>> ts_block = new ArrayList<>();
      ArrayList<ArrayList<Integer>> ts_block_reorder = new ArrayList<>();
      for(int j=0;j<block_size;j++){
        ts_block.add(data.get(j+i*block_size));
        ts_block_reorder.add(data.get(j+i*block_size));
      }

      ArrayList<Integer> deviation_list = new ArrayList<>();
      ArrayList<Integer> result = new ArrayList<>();
      splitTimeStamp(ts_block,block_size,td,deviation_list,result);
      quickSort(ts_block,0,0,block_size-1);

      //ts_block order by interval

      // time-order
      ArrayList<Integer> raw_length = new ArrayList<>(); // length,max_bit_width_interval,max_bit_width_value,max_bit_width_deviation
      ArrayList<Integer> i_star_ready = new ArrayList<>();

      ArrayList<ArrayList<Integer>> ts_block_delta = getEncodeBitsDelta( ts_block,  block_size, raw_length,i_star_ready);


      // value-order
      quickSort(ts_block,1,0,block_size-1);
      ArrayList<Integer> reorder_length = new ArrayList<>();
      ArrayList<Integer> i_star_ready_reorder = new ArrayList<>();
      ArrayList<ArrayList<Integer>> ts_block_delta_reorder = getEncodeBitsDelta( ts_block,  block_size, reorder_length,i_star_ready_reorder);


      if(raw_length.get(0)<=reorder_length.get(0)){
        quickSort(ts_block,0,0,block_size-1);
//        System.out.println(ts_block);
        int i_star = i_star_ready.get(1);
        int j_star;
        count_raw ++;
//        i_star =getIStar(ts_block,block_size,raw_length,0);
        j_star =getJStar(ts_block,i_star,block_size,raw_length,0);

        while(j_star!=0){
          ArrayList<Integer> tmp_tv = ts_block_reorder.get(i_star);
          if(j_star<i_star){
            for(int u=i_star-1;u>=j_star;u--){
              ArrayList<Integer> tmp_tv_cur = new ArrayList<>();
              tmp_tv_cur.add(ts_block_reorder.get(u).get(0));
              tmp_tv_cur.add(ts_block_reorder.get(u).get(1));
              ts_block.set(u+1,tmp_tv_cur);
            }
          }else{
            for(int u=i_star+1;u<=j_star;u++){
              ArrayList<Integer> tmp_tv_cur = new ArrayList<>();
              tmp_tv_cur.add(ts_block_reorder.get(u).get(0));
              tmp_tv_cur.add(ts_block_reorder.get(u).get(1));
              ts_block.set(u-1,tmp_tv_cur);
            }
          }
          ts_block.set(j_star,tmp_tv);
          i_star =getIStar(ts_block,block_size,raw_length,0);
          j_star =getJStar(ts_block,i_star,block_size,raw_length,0);
          System.out.println("adjust");
        }

        ts_block_delta = getEncodeBitsDelta( ts_block,  block_size,raw_length,
                i_star_ready_reorder);
        raw_length.add(result.get(0)); // max_bit_width_deviation
        raw_length.add(result.get(1)); // r0
        raw_length.add(result.get(2)); // d0
        ArrayList<Byte> cur_encoded_result = encode2Bytes(ts_block_delta,deviation_list,raw_length);
        encoded_result.addAll(cur_encoded_result);
        count_raw ++;
      }
      else{
        // adjust to reduce max_bit_width_r
//        System.out.println(ts_block);
        int i_star = i_star_ready_reorder.get(0);
        int j_star = 0;
        ArrayList<Integer> j_star_list =new ArrayList<>();
        count_reorder ++;
//        i_star =getIStar(ts_block,block_size,raw_length,0);
        j_star =getJStar(ts_block,i_star,block_size,raw_length,0);
        while(j_star != 0){
          ArrayList<Integer> tmp_tv = ts_block_reorder.get(i_star);
          if(j_star<i_star){
            for(int u=i_star-1;u>=j_star;u--){
              ArrayList<Integer> tmp_tv_cur = new ArrayList<>();
              tmp_tv_cur.add(ts_block_reorder.get(u).get(0));
              tmp_tv_cur.add(ts_block_reorder.get(u).get(1));
              ts_block.set(u+1,tmp_tv_cur);
            }
          }else{
            for(int u=i_star+1;u<=j_star;u++){
              ArrayList<Integer> tmp_tv_cur = new ArrayList<>();
              tmp_tv_cur.add(ts_block_reorder.get(u).get(0));
              tmp_tv_cur.add(ts_block_reorder.get(u).get(1));
              ts_block.set(u-1,tmp_tv_cur);
            }
          }
          System.out.println("adjust");
          ts_block.set(j_star,tmp_tv);
          i_star =getIStar(ts_block,block_size,raw_length,0);
          j_star =getJStar(ts_block,i_star,block_size,raw_length,0);
        }

        ts_block_delta_reorder = getEncodeBitsDelta( ts_block,  block_size,reorder_length,
                i_star_ready_reorder);
        reorder_length.add(result.get(0)); // max_bit_width_deviation
        reorder_length.add(result.get(1)); // r0
        reorder_length.add(result.get(2)); // d0
        ArrayList<Byte> cur_encoded_result = encode2Bytes(ts_block_delta_reorder,deviation_list,reorder_length);
        encoded_result.addAll(cur_encoded_result);
        count_reorder ++;
      }
    }
//    System.out.println(count_raw);
//    System.out.println(count_reorder);
    return encoded_result;
  }

  public static int bytes2Integer(ArrayList<Byte> encoded, int start, int num) {
    int value = 0;
    if(num > 4){
      System.out.println("bytes2Integer error");
      return 0;
    }
    for (int i = start; i < start + num; i++) {
      value <<= 8;
      int b = encoded.get(i) & 0xFF;
      value |= b;
    }
    return value;
  }

  public static ArrayList<ArrayList<Integer>> ReorderingDeltaDecoder(ArrayList<Byte> encoded,int td){
    ArrayList<ArrayList<Integer>> data = new ArrayList<>();
    int decode_pos = 0;
    int block_size = bytes2Integer(encoded, decode_pos, 4);
    decode_pos += 4;

    while(decode_pos < encoded.size()) {
      ArrayList<Integer> interval_list = new ArrayList<>();
      ArrayList<Integer> value_list = new ArrayList<>();
      ArrayList<Integer> deviation_list = new ArrayList<>();

      ArrayList<ArrayList<Integer>> ts_block = new ArrayList<>();

      int r0 = bytes2Integer(encoded, decode_pos, 4);
      decode_pos += 4;
      int d0 = bytes2Integer(encoded, decode_pos, 4);
      decode_pos += 4;

      if (d0 % 2 == 0) {
        d0 = d0 / 2;
      } else {
        d0 = -(d0 + 1) / 2;
      }

      int min_delta_interval = bytes2Integer(encoded, decode_pos, 4);
      decode_pos += 4;
      int min_delta_value = bytes2Integer(encoded, decode_pos, 4);
      decode_pos += 4;

      int interval0 = bytes2Integer(encoded, decode_pos, 4);
      decode_pos += 4;
      int value0 = bytes2Integer(encoded, decode_pos, 4);
      decode_pos += 4;

      int max_bit_width_interval = bytes2Integer(encoded, decode_pos, 4);
      decode_pos += 4;
      interval_list = decodebitPacking(encoded,decode_pos,max_bit_width_interval,min_delta_interval,block_size);
      decode_pos += max_bit_width_interval * (block_size - 1) / 8;

      int max_bit_width_value = bytes2Integer(encoded, decode_pos, 4);
      decode_pos += 4;
      value_list = decodebitPacking(encoded,decode_pos,max_bit_width_value,min_delta_value,block_size);
      decode_pos += max_bit_width_value * (block_size - 1) / 8;

      int max_bit_width_deviation = bytes2Integer(encoded, decode_pos, 4);
      decode_pos += 4;
      deviation_list = decodebitPacking(encoded,decode_pos,max_bit_width_deviation,0,block_size);
      decode_pos += max_bit_width_deviation * (block_size - 1) / 8;

//      for (int i = 0; i < block_size-1; i++) {
//        ArrayList<Integer> ts_block_tmp = new ArrayList<>();
//        ts_block_tmp.add(interval_list.get(i));
//        ts_block_tmp.add(value_list.get(i));
//        ts_block.add(ts_block_tmp);
//      }

//      ArrayList<Integer> tmp_data = new ArrayList<>();
//      int timestamp = r0 * td + d0;
//      tmp_data.add(timestamp);
//      tmp_data.add(value0);
//      data.add(tmp_data);

      int ri_pre = interval0;
      int vi_pre = value0;
      ArrayList<Integer> ts_block_tmp0 = new ArrayList<>();
      ts_block_tmp0.add(interval0);
      ts_block_tmp0.add(value0);
      ts_block.add(ts_block_tmp0);
      for (int i = 0; i < block_size-1; i++) {
        int ri = ri_pre + interval_list.get(i);
        interval_list.set(i,ri);
        ri_pre = ri;

        int vi = vi_pre + value_list.get(i);
        value_list.set(i,vi);
        vi_pre = vi;

        int dev; //zigzag
        if (deviation_list.get(block_size-1 - i - 1) % 2 == 0) {
          dev = deviation_list.get(block_size-1 - i - 1) / 2;
        } else {
          dev = -(deviation_list.get(block_size-1 - i - 1) + 1) / 2;
        }
        deviation_list.set(block_size-1 - i - 1,dev);

        ArrayList<Integer> ts_block_tmp = new ArrayList<>();
        ts_block_tmp.add(interval_list.get(i));
        ts_block_tmp.add(value_list.get(i));
        ts_block.add(ts_block_tmp);
      }

//      for(int i=0;i<block_size-1;i++){
//        for(int j=0;j<block_size-1 -i -1;j++){
//          if(interval_list.get(j)>interval_list.get(j+1)){
//            int tmp_1 = interval_list.get(j);
//            interval_list.set(j,interval_list.get(j+1));
//            interval_list.set(j,tmp_1);
//            int tmp_2 = value_list.get(j);
//            value_list.set(j,value_list.get(j+1));
//            value_list.set(j,tmp_2);
//          }
//        }
//      }

      //quickSort(ts_block, 0, 0, block_size-2);
      quickSort22(ts_block, 0, block_size-1);

      ArrayList<Integer> tmp_data0 = new ArrayList<>();
      tmp_data0.add(ts_block.get(0).get(0) * td + d0 + r0 * td);
      tmp_data0.add(ts_block.get(0).get(1));
      data.add(tmp_data0);

      for (int i = 1; i < block_size; i++) {
        ArrayList<Integer> tmp_datai = new ArrayList<>();
        tmp_datai.add(ts_block.get(i).get(0) * td + deviation_list.get(i-1) + r0 * td);
        tmp_datai.add(ts_block.get(i).get(1));
        data.add(tmp_datai);
      }

//      for (int i = 0; i < block_size-1; i++) {
//        ArrayList<Integer> tmp_datai = new ArrayList<>();
//        tmp_datai.add(interval_list.get(i) * td + deviation_list.get(i) + r0 * td + d0);
//        tmp_datai.add(value_list.get(i));
//        data.add(tmp_datai);
//      }

//      for (int i = 0; i < block_size-1; i++) {
//        //int vi = vi_pre + value_list.get(i);
//        int vi = vi_pre + ts_block.get(i).get(1);
//        vi_pre = vi;
//
//        int ri = r0 * td + ts_block.get(i).get(0) * td;
//
//        int dev; //zigzag
//        if (deviation_list.get(block_size-1 - i - 1) % 2 == 0) {
//          dev = deviation_list.get(block_size-1 - i - 1) / 2;
//        } else {
//          dev = -(deviation_list.get(block_size-1 - i - 1) + 1) / 2;
//        }
//        int di = di_pre + dev;
//        di_pre = di;
//
//        int timestampi = ri + (di + d0);
//
//        ArrayList<Integer> tmp_datai = new ArrayList<>();
//        tmp_datai.add(timestampi);
//        tmp_datai.add(vi);
//        data.add(tmp_datai);
//      }
    }
    return data;
  }

  public static ArrayList<Integer> decodebitPacking(ArrayList<Byte> encoded,int decode_pos,int bit_width,int min_delta,int block_size){
    ArrayList<Integer> result_list = new ArrayList<>();
    for (int i = 0; i < (block_size-1) / 8; i++) { //bitpacking  纵向8个，bit width是多少列
      int[] val8 = new int[8];
      for (int j = 0; j < 8; j++) {
        val8[j] = 0;
      }
      for (int j = 0; j < bit_width; j++) {
        byte tmp_byte = encoded.get(decode_pos + bit_width - 1 - j);
        byte[] bit8 = new byte[8];
        for (int k = 0; k <8 ; k++) {
          bit8[k] = (byte) (tmp_byte & 1);
          tmp_byte = (byte) (tmp_byte >> 1);
        }
        for (int k = 0; k < 8; k++) {
          val8[k] = val8[k] * 2 + bit8[k];
        }
      }
      for (int j = 0; j < 8; j++) {
        result_list.add(val8[j] + min_delta);
      }
      decode_pos += bit_width;
    }
    return result_list;
  }

  public static void main(@org.jetbrains.annotations.NotNull String[] args) throws IOException {
    ArrayList<String> input_path_list = new ArrayList<>();
    ArrayList<String> output_path_list = new ArrayList<>();
    ArrayList<Integer> dataset_map_td = new ArrayList<>();

    //    input_path_list.add("C:\\Users\\xiaoj\\Documents\\GitHub\\encoding-reorder\\reorder\\iotdb_test\\Metro-Traffic");
//    output_path_list.add("C:\\Users\\xiaoj\\Documents\\GitHub\\encoding-reorder\\reorder\\result_evaluation" +
//            "\\compression_ratio\\rd_ratio\\Metro-Traffic_ratio.csv");
//    dataset_map_td.add(3600);
//    input_path_list.add("C:\\Users\\xiaoj\\Documents\\GitHub\\encoding-reorder\\reorder\\iotdb_test\\Nifty-Stocks");
//    output_path_list.add("C:\\Users\\xiaoj\\Documents\\GitHub\\encoding-reorder\\reorder\\result_evaluation" +
//            "\\compression_ratio\\rd_ratio\\Nifty-Stocks_ratio.csv");
//    dataset_map_td.add(86400);
//    input_path_list.add("C:\\Users\\xiaoj\\Documents\\GitHub\\encoding-reorder\\reorder\\iotdb_test\\USGS-Earthquakes");
//    output_path_list.add("C:\\Users\\xiaoj\\Documents\\GitHub\\encoding-reorder\\reorder\\result_evaluation" +
//            "\\compression_ratio\\rd_ratio\\USGS-Earthquakes_ratio.csv");
//    dataset_map_td.add(50);
//    input_path_list.add("C:\\Users\\xiaoj\\Documents\\GitHub\\encoding-reorder\\reorder\\iotdb_test\\Cyber-Vehicle");
//    output_path_list.add("C:\\Users\\xiaoj\\Documents\\GitHub\\encoding-reorder\\reorder\\result_evaluation" +
//            "\\compression_ratio\\rd_ratio\\Cyber-Vehicle_ratio.csv");
//    dataset_map_td.add(10);
//    input_path_list.add( "C:\\Users\\xiaoj\\Documents\\GitHub\\encoding-reorder\\reorder\\iotdb_test\\TH-Climate");
//    output_path_list.add("C:\\Users\\xiaoj\\Documents\\GitHub\\encoding-reorder\\reorder\\result_evaluation" +
//            "\\compression_ratio\\rd_ratio\\TH-Climate_ratio.csv");
//    dataset_map_td.add(4);
//    input_path_list.add("C:\\Users\\xiaoj\\Documents\\GitHub\\encoding-reorder\\reorder\\iotdb_test\\TY-Transport");
//    output_path_list.add("C:\\Users\\xiaoj\\Documents\\GitHub\\encoding-reorder\\reorder\\result_evaluation" +
//            "\\compression_ratio\\rd_ratio\\TY-Transport_ratio.csv");
//    dataset_map_td.add(6);
//    input_path_list.add( "C:\\Users\\xiaoj\\Documents\\GitHub\\encoding-reorder\\reorder\\iotdb_test\\TY-Fuel");
//    output_path_list.add("C:\\Users\\xiaoj\\Documents\\GitHub\\encoding-reorder\\reorder\\result_evaluation" +
//            "\\compression_ratio\\rd_ratio\\TY-Fuel_ratio.csv");
//    dataset_map_td.add(60);
//    input_path_list.add( "C:\\Users\\xiaoj\\Documents\\GitHub\\encoding-reorder\\reorder\\iotdb_test\\GW-Magnetic");
//    output_path_list.add("C:\\Users\\xiaoj\\Documents\\GitHub\\encoding-reorder\\reorder\\result_evaluation" +
//            "\\compression_ratio\\rd_ratio\\GW-Magnetic_ratio.csv");
//    dataset_map_td.add(100);

    input_path_list.add("E:\\thu\\Lab\\Group\\31编码论文\\mycode-encoding-reorder\\reorder\\iotdb_test\\Metro-Traffic");
    output_path_list.add("E:\\thu\\Lab\\Group\\31编码论文\\mycode-encoding-reorder\\reorder\\result_evaluation" +
            "\\compression_ratio\\rd_ratio\\Metro-Traffic_ratio.csv");
    dataset_map_td.add(3600);
    input_path_list.add("E:\\thu\\Lab\\Group\\31编码论文\\mycode-encoding-reorder\\reorder\\iotdb_test\\Nifty-Stocks");
    output_path_list.add("E:\\thu\\Lab\\Group\\31编码论文\\mycode-encoding-reorder\\reorder\\result_evaluation" +
            "\\compression_ratio\\rd_ratio\\Nifty-Stocks_ratio.csv");
    dataset_map_td.add(86400);
    input_path_list.add("E:\\thu\\Lab\\Group\\31编码论文\\mycode-encoding-reorder\\reorder\\iotdb_test\\USGS-Earthquakes");
    output_path_list.add("E:\\thu\\Lab\\Group\\31编码论文\\mycode-encoding-reorder\\reorder\\result_evaluation" +
            "\\compression_ratio\\rd_ratio\\USGS-Earthquakes_ratio.csv");
    dataset_map_td.add(50);
    input_path_list.add("E:\\thu\\Lab\\Group\\31编码论文\\mycode-encoding-reorder\\reorder\\iotdb_test\\Cyber-Vehicle");
    output_path_list.add("E:\\thu\\Lab\\Group\\31编码论文\\mycode-encoding-reorder\\reorder\\result_evaluation" +
            "\\compression_ratio\\rd_ratio\\Cyber-Vehicle_ratio.csv");
    dataset_map_td.add(10);
    input_path_list.add( "E:\\thu\\Lab\\Group\\31编码论文\\mycode-encoding-reorder\\reorder\\iotdb_test\\TH-Climate");
    output_path_list.add("E:\\thu\\Lab\\Group\\31编码论文\\mycode-encoding-reorder\\reorder\\result_evaluation" +
            "\\compression_ratio\\rd_ratio\\TH-Climate_ratio.csv");
    dataset_map_td.add(3);
    input_path_list.add("E:\\thu\\Lab\\Group\\31编码论文\\mycode-encoding-reorder\\reorder\\iotdb_test\\TY-Transport");
    output_path_list.add("E:\\thu\\Lab\\Group\\31编码论文\\mycode-encoding-reorder\\reorder\\result_evaluation" +
            "\\compression_ratio\\rd_ratio\\TY-Transport_ratio.csv");
    dataset_map_td.add(5);
    input_path_list.add( "E:\\thu\\Lab\\Group\\31编码论文\\mycode-encoding-reorder\\reorder\\iotdb_test\\TY-Fuel");
    output_path_list.add("E:\\thu\\Lab\\Group\\31编码论文\\mycode-encoding-reorder\\reorder\\result_evaluation" +
            "\\compression_ratio\\rd_ratio\\TY-Fuel_ratio.csv");
    dataset_map_td.add(60);


    for(int file_i=0;file_i<input_path_list.size();file_i++){
//    for(int file_i=0;file_i<1;file_i++){
      String inputPath = input_path_list.get(file_i);
      String Output =output_path_list.get(file_i);


      //    String Output =
      //            "C:\\Users\\xiaoj\\Desktop\\test_ratio.csv"; // the direction of output compression ratio and

      // speed
      int repeatTime = 1; // set repeat time

      File file = new File(inputPath);
      File[] tempList = file.listFiles();

      CsvWriter writer = new CsvWriter(Output, ',', StandardCharsets.UTF_8);

      String[] head = {
              "Input Direction",
              "Encoding Algorithm",
              //      "Compress Algorithm",
              "Encoding Time",
              "Decoding Time",
              //      "Compress Time",
              //      "Uncompress Time",
              "Points",
              "Compressed Size",
              "Compression Ratio"
      };
      writer.writeRecord(head); // write header to output file

      assert tempList != null;

      for (File f : tempList) {
        InputStream inputStream = Files.newInputStream(f.toPath());
        CsvReader loader = new CsvReader(inputStream, StandardCharsets.UTF_8);
        ArrayList<ArrayList<Integer>> data = new ArrayList<>();
        ArrayList<ArrayList<Integer>> data_decoded = new ArrayList<>();

        // add a column to "data"
        loader.readHeaders();
        data.clear();
        while (loader.readRecord()) {
          ArrayList<Integer> tmp = new ArrayList<>();
          tmp.add(Integer.valueOf(loader.getValues()[0]));
          tmp.add(Integer.valueOf(loader.getValues()[1]));
          data.add(tmp);
        }
        inputStream.close();
        long encodeTime = 0;
        long decodeTime = 0;
        double ratio = 0;
        double compressed_size = 0;
        for (int i = 0; i < repeatTime; i++) {
          long s = System.nanoTime();
          ArrayList<Byte> buffer = ReorderingDeltaEncoder(data, 256, dataset_map_td.get(file_i));
          long e = System.nanoTime();
          encodeTime += (e - s);
          compressed_size += buffer.size();
          double ratioTmp =
                  (double) buffer.size() / (double) (data.size() * Integer.BYTES*2);
          ratio += ratioTmp;
          s = System.nanoTime();

          data_decoded = ReorderingDeltaDecoder(buffer,dataset_map_td.get(file_i));

//          for(int j=0;j<256;j++){
//            if(!data.get(j).get(0).equals(data_decoded.get(j).get(0))){
//              System.out.println("Wrong Time!");
//              System.out.print(j);
//              System.out.print(" ");
//              System.out.print(data.get(j).get(0));
//              System.out.print(" ");
//              System.out.println(data_decoded.get(j).get(0));
//            }
//            else{
//              System.out.println("Correct Time!");
//              System.out.print(j);
//              System.out.print(" ");
//              System.out.print(data.get(j).get(0));
//              System.out.print(" ");
//              System.out.println(data_decoded.get(j).get(0));
//            }
//            if(!data.get(j).get(1).equals(data_decoded.get(j).get(1))){
//              System.out.println("Wrong Value!");
//              System.out.print(j);
//              System.out.print(" ");
//              System.out.print(data.get(j).get(1));
//              System.out.print(" ");
//              System.out.println(data_decoded.get(j).get(1));
//            }
//            else{
//              System.out.println("Correct Value!");
//              System.out.print(j);
//              System.out.print(" ");
//              System.out.print(data.get(j).get(1));
//              System.out.print(" ");
//              System.out.println(data_decoded.get(j).get(1));
//            }
//          }

          e = System.nanoTime();
          decodeTime += (e-s);
        }


        ratio /= repeatTime;
        compressed_size /= repeatTime;
        encodeTime /= repeatTime;
        decodeTime /= repeatTime;

        String[] record = {
                f.toString(),
                "RD",
                String.valueOf(encodeTime),
                String.valueOf(decodeTime),
                String.valueOf(data.size()),
                String.valueOf(compressed_size),
                String.valueOf(ratio)
        };
        writer.writeRecord(record);
      }
      writer.close();

    }
  }
}

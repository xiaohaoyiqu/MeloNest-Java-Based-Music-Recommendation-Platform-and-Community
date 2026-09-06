   
                      
                                      
   

package com.haoran.music.util;

import java.util.*;

   
          
                      
   
public class PinyinUtil {

       
                                  
                         
       
    private static final Map<String, String[]> PINYIN_DICT = new HashMap<>();

       
              
       
    private static final Map<String, String> INITIAL_DICT = new HashMap<>();

    static {
                                      
                
        addPinyin("周", "zhou");
        addPinyin("杰", "jie");
        addPinyin("伦", "lun");
        addPinyin("陈", "chen");
        addPinyin("奕", "yi");
        addPinyin("迅", "xun");
        addPinyin("林", "lin");
        addPinyin("俊", "jun");
        addPinyin("杰", "jie");
        addPinyin("邓", "deng");
        addPinyin("紫", "zi");
        addPinyin("棋", "qi");
        addPinyin("王", "wang");
        addPinyin("菲", "fei");
        addPinyin("李", "li");
        addPinyin("荣", "rong");
        addPinyin("浩", "hao");
        addPinyin("然", "ran");
        addPinyin("张", "zhang");
        addPinyin("杰", "jie");

                 
        addPinyin("音", "yin");
        addPinyin("乐", "le", "yue");
        addPinyin("歌", "ge");
        addPinyin("曲", "qu");
        addPinyin("摇", "yao");
        addPinyin("滚", "gun");
        addPinyin("流", "liu");
        addPinyin("行", "xing", "hang");
        addPinyin("说", "shuo", "yue");
        addPinyin("唱", "chang");
        addPinyin("跳", "tiao");
        addPinyin("舞", "wu");

               
        addPinyin("刘", "liu");
        addPinyin("赵", "zhao");
        addPinyin("钱", "qian");
        addPinyin("孙", "sun");
        addPinyin("李", "li");
        addPinyin("吴", "wu");
        addPinyin("郑", "zheng");
        addPinyin("冯", "feng");
        addPinyin("陈", "chen");
        addPinyin("楚", "chu");
        addPinyin("卫", "wei");
        addPinyin("蒋", "jiang");
        addPinyin("沈", "shen");
        addPinyin("韩", "han");
        addPinyin("杨", "yang");
        addPinyin("朱", "zhu");
        addPinyin("秦", "qin");
        addPinyin("尤", "you");
        addPinyin("许", "xu");
        addPinyin("何", "he");
        addPinyin("吕", "lv");
        addPinyin("施", "shi");
        addPinyin("张", "zhang");
        addPinyin("孔", "kong");
        addPinyin("曹", "cao");
        addPinyin("严", "yan");
        addPinyin("华", "hua");
        addPinyin("金", "jin");
        addPinyin("魏", "wei");
        addPinyin("陶", "tao");
        addPinyin("姜", "jiang");
        addPinyin("戚", "qi");
        addPinyin("谢", "xie");
        addPinyin("邹", "zou");
        addPinyin("喻", "yu");
        addPinyin("柏", "bai");
        addPinyin("水", "shui");
        addPinyin("窦", "dou");
        addPinyin("章", "zhang");
        addPinyin("云", "yun");
        addPinyin("苏", "su");
        addPinyin("潘", "pan");
        addPinyin("葛", "ge");
        addPinyin("奚", "xi");
        addPinyin("范", "fan");
        addPinyin("彭", "peng");
        addPinyin("郎", "lang");
        addPinyin("鲁", "lu");
        addPinyin("韦", "wei");
        addPinyin("昌", "chang");
        addPinyin("马", "ma");
        addPinyin("苗", "miao");
        addPinyin("凤", "feng");
        addPinyin("花", "hua");
        addPinyin("方", "fang");
        addPinyin("俞", "yu");
        addPinyin("任", "ren");
        addPinyin("袁", "yuan");
        addPinyin("柳", "liu");
        addPinyin("酆", "feng");
        addPinyin("鲍", "bao");
        addPinyin("史", "shi");
        addPinyin("唐", "tang");
        addPinyin("费", "fei");
        addPinyin("廉", "lian");
        addPinyin("岑", "cen");
        addPinyin("薛", "xue");
        addPinyin("雷", "lei");
        addPinyin("贺", "he");
        addPinyin("倪", "ni");
        addPinyin("汤", "tang");
        addPinyin("滕", "teng");
        addPinyin("殷", "yin");
        addPinyin("罗", "luo");
        addPinyin("毕", "bi");
        addPinyin("郝", "hao");
        addPinyin("邬", "wu");
        addPinyin("安", "an");
        addPinyin("常", "chang");
        addPinyin("乐", "le", "yue");
        addPinyin("于", "yu");
        addPinyin("时", "shi");
        addPinyin("傅", "fu");
        addPinyin("皮", "pi");
        addPinyin("卞", "bian");
        addPinyin("齐", "qi");
        addPinyin("康", "kang");
        addPinyin("伍", "wu");
        addPinyin("余", "yu");
        addPinyin("元", "yuan");
        addPinyin("卜", "bu");
        addPinyin("顾", "gu");
        addPinyin("孟", "meng");
        addPinyin("平", "ping");
        addPinyin("黄", "huang");
        addPinyin("和", "he");
        addPinyin("穆", "mu");
        addPinyin("萧", "xiao");
        addPinyin("尹", "yin");
        addPinyin("姚", "yao");
        addPinyin("邵", "shao");
        addPinyin("湛", "zhan");
        addPinyin("汪", "wang");
        addPinyin("祁", "qi");
        addPinyin("毛", "mao");
        addPinyin("禹", "yu");
        addPinyin("狄", "di");
        addPinyin("米", "mi");
        addPinyin("贝", "bei");
        addPinyin("明", "ming");
        addPinyin("臧", "zang");
        addPinyin("计", "ji");
        addPinyin("伏", "fu");
        addPinyin("成", "cheng");
        addPinyin("戴", "dai");
        addPinyin("谈", "tan");
        addPinyin("宋", "song");
        addPinyin("茅", "mao");
        addPinyin("庞", "pang");
        addPinyin("熊", "xiong");
        addPinyin("纪", "ji");
        addPinyin("舒", "shu");
        addPinyin("屈", "qu");
        addPinyin("项", "xiang");
        addPinyin("祝", "zhu");
        addPinyin("董", "dong");
        addPinyin("梁", "liang");
        addPinyin("杜", "du");
        addPinyin("阮", "ruan");
        addPinyin("蓝", "lan");
        addPinyin("闵", "min");
        addPinyin("席", "xi");
        addPinyin("季", "ji");
        addPinyin("麻", "ma");
        addPinyin("强", "qiang");
        addPinyin("贾", "jia");
        addPinyin("路", "lu");
        addPinyin("娄", "lou");
        addPinyin("危", "wei");
        addPinyin("江", "jiang");
        addPinyin("童", "tong");
        addPinyin("颜", "yan");
        addPinyin("郭", "guo");
        addPinyin("梅", "mei");
        addPinyin("盛", "sheng");
        addPinyin("林", "lin");
        addPinyin("刁", "diao");
        addPinyin("钟", "zhong");
        addPinyin("徐", "xu");
        addPinyin("邱", "qiu");
        addPinyin("骆", "luo");
        addPinyin("高", "gao");
        addPinyin("夏", "xia");
        addPinyin("蔡", "cai");
        addPinyin("田", "tian");
        addPinyin("樊", "fan");
        addPinyin("胡", "hu");
        addPinyin("凌", "ling");
        addPinyin("霍", "huo");
        addPinyin("虞", "yu");
        addPinyin("万", "wan");
        addPinyin("支", "zhi");
        addPinyin("柯", "ke");
        addPinyin("咎", "jiu");
        addPinyin("管", "guan");
        addPinyin("卢", "lu");
        addPinyin("莫", "mo");
        addPinyin("经", "jing");
        addPinyin("房", "fang");
        addPinyin("裘", "qiu");
        addPinyin("缪", "miao");
        addPinyin("干", "gan");
        addPinyin("解", "xie");
        addPinyin("应", "ying");
        addPinyin("宗", "zong");
        addPinyin("丁", "ding");
        addPinyin("宣", "xuan");
        addPinyin("邓", "deng");
        addPinyin("郁", "yu");
        addPinyin("单", "shan");
        addPinyin("杭", "hang");
        addPinyin("洪", "hong");
        addPinyin("包", "bao");
        addPinyin("诸", "zhu");
        addPinyin("左", "zuo");
        addPinyin("石", "shi");
        addPinyin("崔", "cui");
        addPinyin("吉", "ji");
        addPinyin("钮", "niu");
        addPinyin("龚", "gong");
        addPinyin("程", "cheng");
        addPinyin("嵇", "ji");
        addPinyin("邢", "xing");
        addPinyin("滑", "hua");
        addPinyin("裴", "pei");
        addPinyin("陆", "lu");
        addPinyin("荣", "rong");
        addPinyin("翁", "weng");
        addPinyin("荀", "xun");
        addPinyin("羊", "yang");
        addPinyin("于", "yu");
        addPinyin("惠", "hui");
        addPinyin("甄", "zhen");
        addPinyin("家", "jia");
        addPinyin("封", "feng");
        addPinyin("芮", "rui");
        addPinyin("羿", "yi");
        addPinyin("储", "chu");
        addPinyin("靳", "jin");
        addPinyin("汲", "ji");
        addPinyin("邴", "bing");
        addPinyin("松", "song");
        addPinyin("井", "jing");
        addPinyin("段", "duan");
        addPinyin("富", "fu");
        addPinyin("巫", "wu");
        addPinyin("乌", "wu");
        addPinyin("焦", "jiao");
        addPinyin("巴", "ba");
        addPinyin("弓", "gong");
        addPinyin("牧", "mu");
        addPinyin("隗", "wei");
        addPinyin("山", "shan");
        addPinyin("谷", "gu");
        addPinyin("车", "che");
        addPinyin("侯", "hou");
        addPinyin("宓", "mi");
        addPinyin("蓬", "peng");
        addPinyin("全", "quan");
        addPinyin("郗", "xi");
        addPinyin("班", "ban");
        addPinyin("仰", "yang");
        addPinyin("秋", "qiu");
        addPinyin("仲", "zhong");
        addPinyin("伊", "yi");
        addPinyin("宫", "gong");
        addPinyin("宁", "ning");
        addPinyin("仇", "qiu");
        addPinyin("栾", "luan");
        addPinyin("暴", "bao");
        addPinyin("甘", "gan");
        addPinyin("斜", "xie", "xia");
        addPinyin("厉", "li");
        addPinyin("戎", "rong");
        addPinyin("祖", "zu");
        addPinyin("武", "wu");
        addPinyin("符", "fu");
        addPinyin("刘", "liu");
        addPinyin("景", "jing");
        addPinyin("詹", "zhan");
        addPinyin("束", "shu");
        addPinyin("龙", "long");
        addPinyin("叶", "ye");
        addPinyin("幸", "xing");
        addPinyin("司", "si");
        addPinyin("韶", "shao");
        addPinyin("郜", "gao");
        addPinyin("黎", "li");
        addPinyin("薄", "bo");
        addPinyin("印", "yin");
        addPinyin("宿", "su");
        addPinyin("白", "bai");
        addPinyin("怀", "huai");
        addPinyin("蒲", "pu");
        addPinyin("台", "tai");
        addPinyin("从", "cong");
        addPinyin("鄂", "e");
        addPinyin("索", "suo");
        addPinyin("咸", "xian");
        addPinyin("籍", "ji");
        addPinyin("赖", "lai");
        addPinyin( "卓", "zhuo");
        addPinyin("蔺", "lin");
        addPinyin("屠", "tu");
        addPinyin("蒙", "meng");
        addPinyin("池", "chi");
        addPinyin("乔", "qiao");
        addPinyin("阴", "yin");
        addPinyin("胥", "xu");
        addPinyin("能", "neng");
        addPinyin("苍", "cang");
        addPinyin("双", "shuang");
        addPinyin("闻", "wen");
        addPinyin("莘", "shen");
        addPinyin("党", "dang");
        addPinyin("翟", "zhai");
        addPinyin("谭", "tan");
        addPinyin("贡", "gong");
        addPinyin("劳", "lao");
        addPinyin("姬", "ji");
        addPinyin("申", "shen");
        addPinyin("扶", "fu");
        addPinyin("堵", "du");
        addPinyin("冉", "ran");
        addPinyin("宰", "zai");
        addPinyin("郦", "li");
        addPinyin("雍", "yong");
        addPinyin("却", "que");
        addPinyin("璩", "qu");
        addPinyin("桑", "sang");
        addPinyin("桂", "gui");
        addPinyin("濮", "pu");
        addPinyin("牛", "niu");
        addPinyin("寿", "shou");
        addPinyin("通", "tong");
        addPinyin("边", "bian");
        addPinyin("扈", "hu");
        addPinyin("燕", "yan");
        addPinyin("冀", "ji");
        addPinyin("郏", "jia");
        addPinyin("浦", "pu");
        addPinyin("尚", "shang");
        addPinyin("农", "nong");
        addPinyin("温", "wen");
        addPinyin("别", "bie");
        addPinyin("庄", "zhuang");
        addPinyin("晏", "yan");
        addPinyin("柴", "chai");
        addPinyin("瞿", "qu");
        addPinyin("阎", "yan");
        addPinyin("充", "chong");
        addPinyin("慕", "mu");
        addPinyin("连", "lian");
        addPinyin("茹", "ru");
        addPinyin("习", "xi");
        addPinyin("宦", "huan");
        addPinyin("艾", "ai");
        addPinyin("鱼", "yu");
        addPinyin("容", "rong");
        addPinyin("向", "xiang");
        addPinyin("古", "gu");
        addPinyin("易", "yi");
        addPinyin("慎", "shen");
        addPinyin("戈", "ge");
        addPinyin("廖", "liao");
        addPinyin("庚", "geng");
        addPinyin("终", "zhong");
        addPinyin("暨", "ji");
        addPinyin("居", "ju");
        addPinyin("衡", "heng");
        addPinyin("步", "bu");
        addPinyin("都", "du");
        addPinyin("耿", "geng");
        addPinyin("满", "man");
                       

                   
        INITIAL_DICT.put("周", "z");
        INITIAL_DICT.put("杰", "j");
        INITIAL_DICT.put("伦", "l");
                     
    }

       
             
       
    private static void addPinyin(String hanzi, String... pinyins) {
        PINYIN_DICT.put(hanzi, pinyins);
                  
        if (pinyins.length > 0 && pinyins[0] != null && pinyins[0].length() > 0) {
            INITIAL_DICT.put(hanzi, pinyins[0].substring(0, 1));
        }
    }

       
                  
                      
                               
       
    public static String[] getPinyin(String hanzi) {
        if (hanzi == null || hanzi.isEmpty()) {
            return new String[0];
        }
        return PINYIN_DICT.getOrDefault(hanzi, new String[]{hanzi});
    }

       
                      
                      
                    
       
    public static String getPinyinFirst(String hanzi) {
        String[] pinyins = getPinyin(hanzi);
        return pinyins.length > 0 ? pinyins[0] : hanzi;
    }

       
                  
                          
                           
       
    public static String toPinyin(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChinese(c)) {
                String[] pinyins = getPinyin(String.valueOf(c));
                if (pinyins.length > 0) {
                    result.append(pinyins[0]);
                } else {
                    result.append(c);
                }
            } else {
                result.append(c);
            }
            if (i < text.length() - 1) {
                result.append(" ");
            }
        }
        return result.toString();
    }

       
                         
                          
                       
       
    public static String toPinyinInitial(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChinese(c)) {
                String initial = INITIAL_DICT.getOrDefault(String.valueOf(c),
                    String.valueOf(Character.toLowerCase(c)));
                result.append(initial);
            } else if (Character.isLetter(c)) {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
    }

       
                
       
    private static boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FA5;
    }

       
                  
       
    public static boolean containsChinese(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (isChinese(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

       
                       
       
    public static boolean isPinyin(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.matches("[a-zA-Z]+");
    }

       
                           
       
    public static boolean isPinyinInitial(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
                  
        return isPinyin(text) && text.length() <= 20;
    }

       
                    
                                               
                       
                      
       
    public static List<String> toSearchVariants(String text) {
        List<String> variants = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return variants;
        }

             
        variants.add(text);

                        
        if (containsChinese(text)) {
                 
            String pinyin = toPinyin(text).replace(" ", "");
            if (!pinyin.equals(text)) {
                variants.add(pinyin);
            }

                 
            String initial = toPinyinInitial(text);
            if (!initial.equals(text) && !initial.isEmpty()) {
                variants.add(initial);
            }
        }

                                 
        return variants.stream().distinct().collect(java.util.stream.Collectors.toList());
    }

       
             
                             
                        
                               
                   
       
    public static boolean fuzzyMatch(String input, String target) {
        if (input == null || target == null) {
            return false;
        }

        input = input.toLowerCase().trim();
        target = target.toLowerCase().trim();

                  
        if (target.contains(input)) {
            return true;
        }

                             
        if (isPinyin(input)) {
                          
            String targetPinyin = toPinyin(target).replace(" ", "");
            String targetInitial = toPinyinInitial(target);

            if (targetPinyin.contains(input) || targetInitial.contains(input)) {
                return true;
            }
        }

                              
        if (containsChinese(input)) {
            String inputPinyin = toPinyin(input).replace(" ", "");
            String inputInitial = toPinyinInitial(input);

            if (target.contains(inputPinyin) || target.contains(inputInitial)) {
                return true;
            }
        }

        return false;
    }
}

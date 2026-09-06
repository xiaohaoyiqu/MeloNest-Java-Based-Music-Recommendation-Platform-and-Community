package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.DetectCrawler;
import com.haoran.music.vo.album.AlbumVO;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.ArtistService;
import com.haoran.music.vo.artist.ArtistVO;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;






@RestController
@RequestMapping("/artist")
public class ArtistController {

    @Resource
    private ArtistService artistService;







    @ApiLog("获取歌手详情")

    @GetMapping("/info/{id}")
    public Result<ArtistVO> getArtistById(@PathVariable("id") Long id,
                                        @RequestAttribute(value = "userId", required = false) Long userId) {
        ArtistVO result = artistService.getArtistById(id, userId);
        return Result.success(result);
    }












    @ApiLog("查询歌手列表")
    @DetectCrawler(operation = "查询歌手列表", checkReferer = true)

    @GetMapping("/page")
    public Result<IPage<ArtistVO>> pageArtists(PageQuery pageQuery,
                                              @RequestParam(value = "area", required = false) String area,
                                              @RequestParam(value = "keyword", required = false) String keyword,
                                              @RequestParam(value = "initial", required = false) String initial,
                                              @RequestParam(value = "sortBy", required = false) String sortBy,
                                              @RequestAttribute(value = "userId", required = false) Long userId) {
        IPage<ArtistVO> result = artistService.pageArtists(pageQuery, area, keyword, initial, sortBy, userId);
        return Result.success(result);
    }







    @ApiLog("按首字母查询歌手")

    @GetMapping("/letter/{letter}")
    public Result<List<ArtistVO>> getArtistsByLetter(@PathVariable("letter") String letter,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<ArtistVO> result = artistService.getArtistsByLetter(letter, userId);
        return Result.success(result);
    }







    @ApiLog("获取热门歌手")

    @GetMapping("/hot")
    public Result<List<ArtistVO>> getHotArtists( @RequestParam(defaultValue = "50") Integer limit,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<ArtistVO> result = artistService.getHotArtists(limit, userId);
        return Result.success(result);
    }







    @ApiLog("获取歌手列表")

    @GetMapping("/list")
    public Result<List<ArtistVO>> getArtistList(
            @RequestParam(value = "area", required = false) String area,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<ArtistVO> result = artistService.getArtistList(area, userId);
        return Result.success(result);
    }








    @ApiLog("搜索歌手")

    @GetMapping("/search")
    public Result<IPage<ArtistVO>> searchArtists( @RequestParam String keyword,
            PageQuery pageQuery,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        IPage<ArtistVO> result = artistService.searchArtists(keyword, pageQuery, userId);
        return Result.success(result);
    }






    @ApiLog("获取首字母列表")

    @GetMapping("/letters")
    public Result<List<String>> getArtistLetters() {
        List<String> result = artistService.getArtistLetters();
        return Result.success(result);
    }







    @ApiLog("关注歌手")

    @PostMapping("/follow/{artistId}")
    public Result<Void> followArtist(@PathVariable("artistId") Long artistId,
                                   @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        artistService.followArtist(userId, artistId);
        return Result.success();
    }







    @ApiLog("取消关注歌手")

    @DeleteMapping("/follow/{artistId}")
    public Result<Void> unfollowArtist(@PathVariable("artistId") Long artistId,
                                     @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        artistService.unfollowArtist(userId, artistId);
        return Result.success();
    }








    @ApiLog("获取歌手热门歌曲")

    @GetMapping("/{artistId}/songs")
    public Result<List<AlbumVO.SongSimpleVO>> getArtistHotSongs(
            @PathVariable("artistId") Long artistId, @RequestParam(defaultValue = "10") Integer limit) {
        List<AlbumVO.SongSimpleVO> result = artistService.getArtistHotSongs(artistId, limit, null);
        return Result.success(result);
    }








    @ApiLog("获取相似歌手推荐")
    @GetMapping("/{artistId}/similar")
    public Result<List<ArtistVO>> getSimilarArtists(@PathVariable("artistId") Long artistId,
                                                     @RequestParam(defaultValue = "10") Integer limit) {
        List<ArtistVO> result = artistService.getSimilarArtists(artistId, limit);
        return Result.success(result);
    }
}

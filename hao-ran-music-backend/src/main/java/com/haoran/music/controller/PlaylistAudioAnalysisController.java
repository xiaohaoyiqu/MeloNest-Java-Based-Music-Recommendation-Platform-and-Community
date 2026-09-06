package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.vo.audio.PlaylistAudioAnalysis;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.PlaylistAudioAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

   
                                                                                
  
                      
   
@RestController
@RequestMapping("/playlist-audio")
public class PlaylistAudioAnalysisController {

    private final PlaylistAudioAnalysisService playlistAudioAnalysisService;

    public PlaylistAudioAnalysisController(PlaylistAudioAnalysisService playlistAudioAnalysisService) {
        this.playlistAudioAnalysisService = playlistAudioAnalysisService;
    }

       
                                                                    
       
    @ApiLog
    @GetMapping("/analyze/{playlistId}")
    public Result<PlaylistAudioAnalysis> analyzePlaylist(@PathVariable Long playlistId) {
        return Result.success(playlistAudioAnalysisService.analyzePlaylist(playlistId));
    }

       
                                                                  
       
    @ApiLog
    @GetMapping("/tags/{playlistId}")
    public Result<List<String>> generateAutoTags(@PathVariable Long playlistId) {
        return Result.success(playlistAudioAnalysisService.generateAutoTags(playlistId));
    }

       
                                                     
       
    @ApiLog
    @PostMapping("/update-tags/{playlistId}")
    public Result<Map<String, Object>> updatePlaylistAudioTags(@PathVariable Long playlistId,
                                                               @RequestAttribute(value = "userId", required = false)
                                                               Long operatorId) {
        return Result.success(playlistAudioAnalysisService.updatePlaylistAudioTags(playlistId, operatorId));
    }

       
                                                          
       
    @ApiLog
    @GetMapping("/distribution/{playlistId}")
    public Result<Map<String, Object>> getPlaylistFeatureDistribution(@PathVariable Long playlistId) {
        return Result.success(playlistAudioAnalysisService.getPlaylistFeatureDistribution(playlistId));
    }

       
                                                       
       
    @ApiLog
    @GetMapping("/consistency/{playlistId}")
    public Result<Double> checkPlaylistConsistency(@PathVariable Long playlistId) {
        return Result.success(playlistAudioAnalysisService.checkPlaylistConsistency(playlistId));
    }

       
                                                                      
       
    @ApiLog
    @GetMapping("/recommend")
    public Result<List<Long>> recommendPlaylistsByFeatures(@RequestParam Double valence,
                                                           @RequestParam Double energy,
                                                           @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(playlistAudioAnalysisService.recommendPlaylistsByFeatures(valence, energy, limit));
    }

       
                                                                                 
       
    @ApiLog
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/batch-update")
    public Result<Integer> batchUpdateAllPlaylists() {
        return Result.success(playlistAudioAnalysisService.batchUpdateAllPlaylists());
    }
}

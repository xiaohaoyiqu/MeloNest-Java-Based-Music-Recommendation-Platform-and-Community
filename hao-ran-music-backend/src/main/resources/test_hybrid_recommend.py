#!/usr/bin/env python3
                       
\
\
\
\
\
   

import os
import requests
import json
import time
from datetime import datetime
from typing import Dict, List, Any

    
BASE_URL = os.getenv("HAORAN_API_BASE_URL", "http://192.168.153.131:9090/api")
TEST_USER_ID = os.getenv("HAORAN_TEST_USER_ID", "2032779181554094081")

      
test_results = []


def log_result(test_name: str, success: bool, data: Any = None, error: str = None):
                
    result = {
        "test_name": test_name,
        "success": success,
        "timestamp": datetime.now().isoformat(),
        "data": data,
        "error": error
    }
    test_results.append(result)

    status = "✅ PASS" if success else "❌ FAIL"
    print(f"{status} | {test_name}")
    if error:
        print(f"    错误: {error}")
    if data and isinstance(data, dict):
        print(f"    数据: {json.dumps(data, ensure_ascii=False)[:200]}")


def test_hybrid_recommend():
                
    print("\n=== 测试混合推荐 ===")
    try:
        response = requests.get(
            f"{BASE_URL}/hybrid/recommend",
            params={"userId": TEST_USER_ID, "limit": 10},
            timeout=10
        )
        if response.status_code == 200:
            data = response.json()
            song_count = len(data.get("songs", []))
            source = data.get("sourceName", "")
            reason = data.get("reason", "")
            log_result(
                "混合推荐API",
                True,
                {
                    "songs": song_count,
                    "source": source,
                    "reason": reason
                }
            )
            return data
        else:
            log_result("混合推荐API", False, error=f"状态码: {response.status_code}")
    except Exception as e:
        log_result("混合推荐API", False, error=str(e))
    return None


def test_cold_start_recommend():
                 
    print("\n=== 测试冷启动推荐 ===")
    try:
        response = requests.get(
            f"{BASE_URL}/hybrid/cold-start",
            params={"userId": TEST_USER_ID, "limit": 10},
            timeout=10
        )
        if response.status_code == 200:
            data = response.json()
            song_count = len(data.get("songs", []))
            source = data.get("sourceName", "")
            log_result(
                "冷启动推荐API",
                True,
                {"songs": song_count, "source": source}
            )
        else:
            log_result("冷启动推荐API", False, error=f"状态码: {response.status_code}")
    except Exception as e:
        log_result("冷启动推荐API", False, error=str(e))


def test_discovery_recommend():
                  
    print("\n=== 测试发现模式推荐 ===")
    try:
        response = requests.get(
            f"{BASE_URL}/hybrid/discovery",
            params={"userId": TEST_USER_ID, "limit": 10},
            timeout=10
        )
        if response.status_code == 200:
            data = response.json()
            song_count = len(data.get("songs", []))
            log_result("发现推荐API", True, {"songs": song_count})
        else:
            log_result("发现推荐API", False, error=f"状态码: {response.status_code}")
    except Exception as e:
        log_result("发现推荐API", False, error=str(e))


def test_mood_recommend():
                 
    print("\n=== 测试情绪化推荐 ===")
    moods = ["happy", "sad", "energetic", "calm", "focus"]
    for mood in moods:
        try:
            response = requests.get(
                f"{BASE_URL}/hybrid/mood/{mood}",
                params={"userId": TEST_USER_ID, "limit": 5},
                timeout=10
            )
            if response.status_code == 200:
                data = response.json()
                song_count = len(data.get("songs", []))
                reason = data.get("reason", "")
                log_result(
                    f"情绪推荐-{mood}",
                    True,
                    {"songs": song_count, "reason": reason}
                )
            else:
                log_result(f"情绪推荐-{mood}", False, error=f"状态码: {response.status_code}")
        except Exception as e:
            log_result(f"情绪推荐-{mood}", False, error=str(e))


def test_recommend_weights():
                  
    print("\n=== 测试推荐权重配置 ===")
    try:
        response = requests.get(f"{BASE_URL}/hybrid/weights", timeout=10)
        if response.status_code == 200:
            data = response.json()
            if isinstance(data, dict):
                weights = data.get("data", data)
            else:
                weights = {}
            log_result("获取权重配置", True, weights)
        else:
            log_result("获取权重配置", False, error=f"状态码: {response.status_code}")
    except Exception as e:
        log_result("获取权重配置", False, error=str(e))


def test_refresh_profile():
                  
    print("\n=== 测试刷新推荐画像 ===")
    try:
        response = requests.post(
            f"{BASE_URL}/hybrid/refresh",
            params={"userId": TEST_USER_ID},
            timeout=10
        )
        if response.status_code == 200:
            log_result("刷新推荐画像", True)
        else:
            log_result("刷新推荐画像", False, error=f"状态码: {response.status_code}")
    except Exception as e:
        log_result("刷新推荐画像", False, error=str(e))


def test_song_detail():
                  
    print("\n=== 测试歌曲详情 ===")
    try:
                 
        response = requests.get(
            f"{BASE_URL}/hybrid/recommend",
            params={"userId": TEST_USER_ID, "limit": 1},
            timeout=10
        )
        if response.status_code == 200:
            data = response.json()
            songs = data.get("songs", [])
            if songs:
                song_id = songs[0].get("id")
                        
                detail_response = requests.get(
                    f"{BASE_URL}/song/{song_id}",
                    timeout=10
                )
                if detail_response.status_code == 200:
                    log_result("歌曲详情API", True)
                else:
                    log_result("歌曲详情API", False, error=f"状态码: {detail_response.status_code}")
            else:
                log_result("歌曲详情API", False, error="无推荐歌曲")
        else:
            log_result("歌曲详情API", False, error="无法获取推荐歌曲")
    except Exception as e:
        log_result("歌曲详情API", False, error=str(e))


def check_service_health():
                  
    print("\n=== 检查服务健康 ===")
    try:
        response = requests.get(f"{BASE_URL}/health", timeout=5)
        if response.status_code == 200:
            log_result("服务健康检查", True)
            return True
        else:
            log_result("服务健康检查", False, error=f"状态码: {response.status_code}")
    except Exception as e:
        log_result("服务健康检查", False, error=str(e))
    return False


def run_test_round(round_num: int):
                
    print(f"\n{'='*60}")
    print(f"第 {round_num} 轮测试 - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"{'='*60}")

          
    if not check_service_health():
        print("服务不可用，跳过本轮测试")
        return

            
    test_hybrid_recommend()
    test_cold_start_recommend()
    test_discovery_recommend()
    test_mood_recommend()
    test_recommend_weights()
    test_refresh_profile()
    test_song_detail()


def main():
             
    print("\n" + "="*60)
    print("浩然音乐混合推荐系统测试")
    print(f"测试时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"测试用户: {TEST_USER_ID}")
    print(f"后端地址: {BASE_URL}")
    print("="*60)

            
    for i in range(1, 4):
        run_test_round(i)
        if i < 3:
            print(f"\n等待5秒后进行下一轮测试...")
            time.sleep(5)

          
    print("\n" + "="*60)
    print("测试结果汇总")
    print("="*60)

    total = len(test_results)
    passed = sum(1 for r in test_results if r["success"])
    failed = total - passed

    print(f"总测试数: {total}")
    print(f"通过: {passed} ✅")
    print(f"失败: {failed} ❌")
    print(f"通过率: {passed/total*100:.1f}%")

           
    if failed > 0:
        print("\n失败的测试:")
        for r in test_results:
            if not r["success"]:
                print(f"  - {r['test_name']}: {r.get('error', 'Unknown error')}")

          
    result_file = f"/sdb1/myprojoct/haoranmusic/logs/test_results_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    with open(result_file, 'w', encoding='utf-8') as f:
        json.dump({
            "timestamp": datetime.now().isoformat(),
            "total": total,
            "passed": passed,
            "failed": failed,
            "pass_rate": f"{passed/total*100:.1f}%",
            "results": test_results
        }, f, ensure_ascii=False, indent=2)
    print(f"\n测试结果已保存至: {result_file}")


if __name__ == "__main__":
    main()

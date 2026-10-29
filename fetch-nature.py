"""Prepare optional local nature footage; requires FFmpeg on PATH."""
from pathlib import Path
import subprocess
out=Path(__file__).resolve().parent/'assets/ambient/nature.mp4'
out.parent.mkdir(parents=True,exist_ok=True)
subprocess.run(['ffmpeg','-n','-i','https://assets.mixkit.co/videos/572/572-1080.mp4','-t','30','-an','-vf','scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2','-c:v','libx264','-preset','fast','-crf','18','-maxrate','12M','-bufsize','24M','-pix_fmt','yuv420p','-movflags','+faststart',str(out)],check=True)

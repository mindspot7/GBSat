#!/usr/bin/env python3
"""Generate the OpenGL ES 3.1 variants of Ymir's VDP compute shaders.

Requires DXC with SPIR-V output enabled and the SPIRV-Cross command-line tool.
The Android runtime consumes the checked-in GLSL files; these tools are only
needed when one of the HLSL sources or specialization variants changes.
"""

from __future__ import annotations

import argparse
import os
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SHADER_ROOT = ROOT / "res" / "shaders"
SOURCE_ROOT = SHADER_ROOT / "src" / "vdp"
OUTPUT_ROOT = SHADER_ROOT / "gles" / "vdp"


def variants() -> list[tuple[str, str, tuple[str, ...]]]:
    result = [
        ("cs_vdp1_erase", "cs_vdp1_erase", ()),
        ("cs_vdp1_fbram_write", "cs_vdp1_fbram_write", ()),
        ("cs_vdp1_fbram_downsample", "cs_vdp1_fbram_downsample", ()),
        ("cs_vdp2_compose", "cs_vdp2_compose", ()),
        ("cs_vdp2_render_bgs", "cs_vdp2_render_bgs_noint64", ("YMIR_NO_INT64=1", "YMIR_GLES=1")),
        ("cs_vdp2_render_sprite", "cs_vdp2_render_sprite", ("YMIR_GLES=1",)),
    ]
    for mesh in range(2):
        for mode in range(4):
            result.append(
                (
                    "cs_vdp1_polydraw",
                    f"cs_vdp1_polydraw_{mesh}_{mode}",
                    (f"POLYSPEC_TRANSPARENT_MESH={mesh}", f"POLYSPEC_SHADING_MODE={mode}"),
                )
            )
        for mode in range(3):
            result.append(
                (
                    "cs_vdp1_output_merger",
                    f"cs_vdp1_output_merger_{mesh}_{mode}",
                    (f"POLYSPEC_TRANSPARENT_MESH={mesh}", f"POLYSPEC_MERGE_MODE={mode}"),
                )
            )
    return result


def run(command: list[str]) -> None:
    subprocess.run(command, check=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dxc", default=os.environ.get("DXC"), help="DXC executable (or set DXC)")
    parser.add_argument(
        "--spirv-cross",
        default=os.environ.get("SPIRV_CROSS"),
        help="SPIRV-Cross executable (or set SPIRV_CROSS)",
    )
    parser.add_argument("--output", type=Path, default=OUTPUT_ROOT)
    args = parser.parse_args()
    if not args.dxc or not args.spirv_cross:
        parser.error("provide --dxc and --spirv-cross (or set DXC and SPIRV_CROSS)")

    with tempfile.TemporaryDirectory(prefix="ymir-gles-shaders-") as temporary:
        temporary_path = Path(temporary)
        for source_name, output_name, defines in variants():
            spirv = temporary_path / f"{output_name}.spv"
            glsl = args.output / f"{output_name}.comp.glsl"
            glsl.parent.mkdir(parents=True, exist_ok=True)

            compile_command = [
                args.dxc,
                "-T", "cs_6_0",
                "-E", "CSMain",
                "-spirv",
                "-fvk-b-shift", "0", "0",
                "-fvk-t-shift", "0", "0",
                "-fvk-u-shift", "16", "0",
                "-fvk-use-gl-layout",
            ]
            for define in defines:
                compile_command.extend(("-D", define))
            compile_command.extend(("-I", str(SHADER_ROOT / "src")))
            compile_command.extend(("-I", str(SHADER_ROOT / "include")))
            compile_command.extend(("-Fo", str(spirv), str(SOURCE_ROOT / f"{source_name}.hlsl")))
            run(compile_command)
            run(
                [
                    args.spirv_cross,
                    str(spirv),
                    "--version", "310",
                    "--es",
                    "--output", str(glsl),
                ]
            )
            print(f"Generated {glsl.relative_to(ROOT)}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

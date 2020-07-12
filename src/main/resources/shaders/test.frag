#version 130

in vec3 passColor;

out vec4 out_Color;

void main() {
    out_Color = vec4(passColor, 1.0);
}